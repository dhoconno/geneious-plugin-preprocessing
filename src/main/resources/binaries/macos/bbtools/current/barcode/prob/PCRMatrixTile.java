package barcode.prob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

import barcode.Barcode;
import shared.Shared;
import shared.Timer;
import shared.Tools;
import structures.ByteBuilder;
import structures.DoubleList;
import structures.IntList;
import template.Accumulator;
import template.ThreadWaiter;

/**
 * Probabilistic barcode matching system that tracks mismatch patterns by position using tile-based processing.
 * Implements split barcode analysis (separate left and right segments) rather than contiguous sequence matching.
 * Uses position-specific error probability matrices to score potential barcode assignments with statistical confidence.
 * Supports both single-threaded and multi-threaded processing for large-scale barcode assignment tasks.
 * 
 * This class is closed-source.
 * It is not legal to redistribute, replicate, or reverse-engineer.
 * 
 * @author Brian Bushnell
 * @date March 25, 2024
 *
 */
public class PCRMatrixTile extends PCRMatrixProbAbstract implements Accumulator<PCRMatrixTile.PopThread> {

	/*--------------------------------------------------------------*/
	/*----------------         Constructor          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Constructs a PCRMatrixTile with specified barcode segment lengths and configuration parameters.
	 * Initializes tile-based probability matrices for position-specific error modeling.
	 * @param length1_ Length of the first (left) barcode segment
	 * @param length2_ Length of the second (right) barcode segment
	 * @param delimiter_ Delimiter position separating barcode segments
	 * @param hdistSum_ Whether to sum Hamming distances across segments
	 */
	public PCRMatrixTile(int length1_, int length2_, int delimiter_, boolean hdistSum_){
		super(length1_, length2_, delimiter_, hdistSum_, true);
	}

	/*--------------------------------------------------------------*/
	/*----------------           Parsing            ----------------*/
	/*--------------------------------------------------------------*/

	/*--------------------------------------------------------------*/
	/*----------------         Probability          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Base findClosest method - not implemented for tile-based processing.
	 * Use the tile-specific version findClosest(String, int, float, float, int) instead.
	 * @param s Query barcode sequence
	 * @return Never returns - throws RuntimeException
	 * @throws RuntimeException Always thrown as this method is not valid for tile-based matrices
	 */
	@Override
	public Barcode findClosest(String s){
		throw new RuntimeException("Not valid for class.");
	}
	/**
	 * Finds the closest matching barcode using tile-specific probability matrices and filtering criteria.
	 * Routes to single-segment or dual-segment matching based on barcode structure configuration.
	 * @param s Query barcode sequence to match
	 * @param maxHDist Maximum allowed Hamming distance for matches
	 * @param minRatio Minimum ratio between best and second-best scores to avoid ambiguous matches
	 * @param minProb Minimum probability threshold for accepting matches
	 * @param tile Tile identifier for position-specific error modeling
	 * @return Best matching Barcode or null if no suitable match found
	 */
	public Barcode findClosest(String s, int maxHDist, float minRatio, float minProb, int tile){
		return length2<1 ? findClosestSingle(s, maxHDist, minRatio, minProb, tile) : 
			findClosestDual(s, maxHDist, minRatio, minProb, tile);
	}
	
	/**
	 * Finds closest matching barcode for single-segment (contiguous) barcode sequences.
	 * Uses probability-weighted scoring with frequency-based ranking and multiple filtering thresholds.
	 * Implements hybrid distance checking for perfect matches before probabilistic scoring.
	 * @param q Query barcode sequence
	 * @param maxHDist Maximum allowed Hamming distance
	 * @param minRatio Minimum ratio to distinguish best from second-best match
	 * @param minProb Minimum probability threshold
	 * @param tile Tile identifier for position-specific error rates
	 * @return Best matching Barcode or null if filtering criteria not met
	 */
	private Barcode findClosestSingle(String q, int maxHDist, float minRatio, float minProb, int tile){
		if(hybridHDist>=0){
			//This forces perfect matches to return self.
			Barcode x=allCodesMap.get(q);
			if(x!=null && x.expected==1){return x;} //Probably doesn't need to be expected
			
			if(hybridHDist>0){
				x=findClosestSingleHDist(q, hybridHDist, hybridClearzone);
				if(x!=null){return x;}
			}
		}
		assert(q.length()==length1);
		final byte[] left=q.getBytes();
//DoubleList plist=getDoubleList(0);
		DoubleList plist=new DoubleList(leftBytes.length);
		fillProbList(left, leftBytes, 0, tile, plist);
		double sum=0;
		double score=0;
		assert(allCodes[0]==leftCodes[0]);
		assert(allCodes.length==leftCodes.length);
		int bestIdx=-1;
		for(int i=0; i<leftBytes.length; i++){
			double raw=plist.get(i);
			double product=raw*allCodes[i].frequency; //Weight probability by barcode frequency
			sum+=(product>score ? score : product); //Accumulate second-best scores for ratio calculation
			bestIdx=(product>score ? i : bestIdx);
			score=(product>score ? product : score);
		}

		assert(score<=1);
		assert(sum>=0);
		if(bestIdx<0){return null;}
		if(sum*minRatio>=score){return null;} //Reject ambiguous matches with poor best/second-best ratio
		Barcode best=allCodes[bestIdx];
		int hdist=hdist(best, q);
		if(hdist>maxHDist){return null;}
		
		if(FBRANCH==0){
			if(score*best.frequency<minProb){return null;}
		}else if(FBRANCH==1){
			if(score<minProb){return null;}
		}else if(FBRANCH==2){
			if(score*best.frequency*20<minProb){return null;}
			if(score<minProb){return null;}
		}else{
			throw new RuntimeException("Unknown FBRANCH");
		}
		
		return best;
	}
	
	/**
	 * Finds closest matching barcode for dual-segment (split) barcode sequences.
	 * Performs combinatorial scoring by computing probability products of left and right segments.
	 * Implements score boosting for expected perfect matches and multi-threshold filtering.
	 * @param q Query barcode sequence with both left and right segments
	 * @param maxHDist Maximum allowed Hamming distance
	 * @param minRatio Minimum ratio to distinguish best from second-best match
	 * @param minProb Minimum probability threshold
	 * @param tile Tile identifier for position-specific error rates
	 * @return Best matching Barcode or null if filtering criteria not met
	 */
	private Barcode findClosestDual(String q, int maxHDist, float minRatio, float minProb, int tile){
		if(hybridHDist>=0){
			//This forces perfect matches to return self.
			Barcode x=allCodesMap.get(q);
			if(x!=null && x.expected==1){return x;} //Probably doesn't need to be expected
			
			if(hybridHDist>0){
				x=findClosestDualHDist(q, hybridHDist, hybridClearzone);
				if(x!=null){return x;}
			}
		}
		//if(verbose) {System.err.println("Looking for "+q);}
		byte[] left=new byte[length1];
		byte[] right=new byte[length2];
		for(int i=0; i<length1; i++){left[i]=(byte) q.charAt(i);} //Extract left segment
		for(int i=length2-1, j=q.length()-1; i>=0; i--, j--){ //Extract right segment in reverse
			right[i]=(byte) q.charAt(j);
		}

//final DoubleList llist=getDoubleList(0);
//final DoubleList rlist=getDoubleList(1);
//final IntList hdrlist=getIntList();
		final DoubleList llist=new DoubleList(leftBytes.length);
		final DoubleList rlist=new DoubleList(rightBytes.length);
		final IntList hdrlist=new IntList(rightBytes.length);
		fillProbList(left, leftBytes, 0, tile, llist); //Score left segment against all left references
		fillProbList(right, rightBytes, start2, tile, rlist); //Score right segment against all right references
		fillHDistList(right, rightBytes, hdrlist);
		double sum=0;
		double score=0;
		int hdist=letters;
		int bestIdx=-1;
		
		for(int i=0, idx=0; i<leftBytes.length; i++){ //Combinatorial scoring across all left-right pairs
			double raw1=llist.get(i);
			for(int j=0; j<rightBytes.length; j++, idx++){
				double raw2=rlist.get(j);
				double product=raw1*raw2*comboFrequency[idx]; //Multiply segment probabilities with combination frequency
				sum+=(product>score ? score : product); //Track second-best scores for ratio test
				bestIdx=(product>score ? idx : bestIdx);
				score=(product>score ? product : score);
			}
		}
		
		if(bestIdx<0){return null;}
		
		assert(score<=1);
		assert(sum>=0);
		Barcode best=allCodes[bestIdx];
		if(best.expected==1 && q.equals(best.name)){ //Boost expected perfect matches
//assert(!q.equals(best.name)) : "\n"+best.name+", "+q+", "+best.expected+", "
//+score+", "+best.hdist(q)+", "+q.equals(best.name)+"\n";
//double f=score;
			score=Tools.max(score, (score+minExpectedScore)*0.5); //Apply score boost for expected matches
//assert(false) : "\n"+best.name+", "+q+", "
//+f+"->"+score+", "+best.hdist(q)
//+"\ncombo="+comboFrequency[bestIdx]+", raw="+f/comboFrequency[bestIdx];
		}
		if(sum*minRatio>=score){return null;} //Reject ambiguous matches
		if(FBRANCH==0){
			if(score*best.frequency<minProb){return null;}
			//Works better for some reason...
		}else if(FBRANCH==1){
			if(score<minProb){return null;}
			//Works OK with "minratio0=4 minratio1=200k minprob0=-12 minprob1=-4.8 fbranch=1"
		}else if(FBRANCH==2){
			if(score*best.frequency*20<minProb){return null;}
			if(score<minProb){return null;}
		}else{
			throw new RuntimeException("Unknown FBRANCH");
		}
		hdist=hdist(best, q);
		if(hdist>maxHDist){return null;}
		
//if(true) {return null;}//This speeds it up by 40x
		return best;
	}
	
	/**
	 * Calculates probability scores for a query sequence against all reference sequences at a specific position.
	 * Uses tile-specific error probability matrices to compute likelihood of each potential match.
	 * @param q Query sequence bytes
	 * @param list Array of reference sequence byte arrays to score against
	 * @param pos Starting position offset for probability matrix lookup
	 * @param tile Tile identifier for position-specific error modeling
	 * @param results DoubleList to store computed probability scores (cleared before use)
	 */
	public void fillProbList(byte[] q, byte[][] list, int pos, int tile, DoubleList results){
		results.clear();
		assert(!expectedList.isEmpty());
		for(int i=0; i<list.length; i++){
			byte[] ref=list[i];
			double s=scoreUsingProbs(q, ref, pos, tile); //Compute probabilistic alignment score
			results.add(s);
		}
	}
	
	/**
	 * Creates assignment mappings from query barcodes to their closest reference matches.
	 * Automatically selects single-threaded or multi-threaded processing based on workload size.
	 * Uses relaxed thresholds for low-count queries to improve assignment rates.
	 * @param codeCounts Collection of query barcodes with their observed counts
	 * @param minCountA Minimum count threshold for strict matching criteria
	 * @param maxHDist Maximum allowed Hamming distance for matches
	 * @param minRatio Minimum ratio between best and second-best scores
	 * @param minProb Minimum probability threshold for matches
	 * @return HashMap mapping query barcode+tile keys to reference barcode names
	 */
	public HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCountA,
			int maxHDist, float minRatio, float minProb){
		writelock();
		Timer t=new Timer();
		assert(expectedList!=null && expectedList.size()>0) : expectedList;
		assert(codeCounts!=null);
		ArrayList<Barcode> list=highpass(codeCounts, 0);
		HashMap<String, String> map=new HashMap<String, String>(Tools.max(200, list.size()/10));
		totalCounted=totalAssigned=totalAssignedToExpected=0;
		final long ops=list.size()*(long)expectedList.size();
		final int threads=Tools.min(Shared.threads(), matrixThreads);
		if(list.size()<2 || ops<100000 || threads<2){ //Singlethreaded mode
			for(Barcode query : list){
				final String s=query.name;
				assert(s.length()==counts.length);
				final Barcode ref;
				if(query.count()>=minCountA){ //High-count queries use strict thresholds
					ref=findClosest(s, maxHDist, minRatio, minProb, query.tile);
				}else{ //Low-count queries use relaxed thresholds
					ref=findClosest(s, maxHDist, minRatio*4, minProb*8, query.tile);
				}
				final long count=query.count();
				totalCounted+=count;
				if(ref!=null){
					totalAssigned+=count;
					if(ref.expected==1){ //Only map to expected (non-chimeric) references
						totalAssignedToExpected+=count;
						map.put(s+query.tile, ref.name);
					}
				}
			}
		}else{
			writeunlock();
			populateCountsMT(list, maxHDist, minRatio, minProb, minCountA, map);
			writelock();
		}
		t.stop();
		if(verbose){
			System.err.println(String.format("Final Assignment Rate:  \t%.4f\t%.4f\t%.6f", 
					assignedFraction(), expectedFraction(), chimericFraction())+"\t"+t.timeInSeconds(2)+"s");
		}
		writeunlock();
		return map;
	}

	/**
	 * Populates count matrices using single-threaded processing for smaller workloads.
	 * Processes each query barcode sequentially, finding closest matches and updating tile-specific counts.
	 * Merges all tile count matrices into the global count matrix after processing.
	 * @param countList List of query barcodes with their observed counts
	 * @param maxHDist Maximum allowed Hamming distance for matches
	 * @param minRatio Minimum ratio between best and second-best scores
	 * @param minProb Minimum probability threshold for matches
	 * @param minCount Minimum count threshold for strict matching criteria
	 */
	void populateCountsST(ArrayList<Barcode> countList,
			int maxHDist, float minRatio, float minProb, long minCount){
		writelock();
		for(Barcode query : countList){
			assert(query.tile>0) : query+", "+query.tile;
			final String s=query.name;
			assert(s.length()==counts.length);
			final Barcode ref;
			if(query.count()>=minCount){ //High-count queries use strict thresholds
				ref=findClosest(s, maxHDist, minRatio, minProb, query.tile);
			}else{ //Low-count queries use relaxed thresholds
				ref=findClosest(s, maxHDist, minRatio*4, minProb*8, query.tile);
			}
			addToTile(s, ref, query.count(), query.tile); //Update tile-specific count matrix
		}
		for(int i=0; i<tileCounts.size(); i++){ //Merge tile counts into global matrix
			long[][][] countsT=tileCounts.get(i);
			if(countsT!=null){
				Tools.add(counts, countsT);
			}
		}
		writeunlock();
	}

	/**
	 * Populates count matrices using multi-threaded processing for large workloads.
	 * Distributes query barcodes across multiple worker threads for parallel processing.
	 * Each thread maintains local count matrices that are accumulated after completion.
	 * @param list List of query barcodes to process
	 * @param maxHDist Maximum allowed Hamming distance for matches
	 * @param minRatio Minimum ratio between best and second-best scores
	 * @param minProb Minimum probability threshold for matches
	 * @param minCount Minimum count threshold for strict matching criteria
	 * @param map Assignment mapping to populate (may be null)
	 */
	void populateCountsMT(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb, long minCount,
			HashMap<String, String> map){
		//Do anything necessary prior to processing
		
		//Determine how many threads may be used
		final int threads=Tools.mid(1, Tools.min(matrixThreads, Shared.threads()), list.size()/8);
		
		//Fill a list with PopThreads
		ArrayList<PopThread> alpt=new ArrayList<PopThread>(threads);
		for(int i=0; i<threads; i++){
			alpt.add(new PopThread(list, maxHDist, minRatio, minProb, minCount, map, i, threads));
		}
		
		//Start the threads and wait for them to finish
		boolean success=ThreadWaiter.startAndWait(alpt, this);
		errorState&=!success;

		writelock();
		//Do anything necessary after processing
		if(localCounts && map!=null){ //Merge local assignment maps from all threads
			for(PopThread pt : alpt){
				synchronized(pt){map.putAll(pt.map);}
			}
		}
		writeunlock();
	}
	
	/**
	 * Computes probabilistic alignment score between two sequences using tile-specific error matrices.
	 * Retrieves appropriate probability matrix for the specified tile, falling back to global matrix if unavailable.
	 * @param a First sequence (query) as byte array
	 * @param b Second sequence (reference) as byte array
	 * @param pos Starting position offset for matrix indexing
	 * @param tile Tile identifier for position-specific error modeling
	 * @return Probabilistic alignment score between sequences
	 */
	public double scoreUsingProbs(final byte[] a, final byte[] b, int pos, int tile){
		float[][][] tprobs=(tile==0 ? probs : getProbsForTile(tile, false));
		if(tprobs==null){tprobs=probs;} //Fallback to global probabilities
		return scoreUsingProbs(a, b, pos, tprobs);
	}
	
	/**
	 * Clears all count matrices and resets barcode counters to zero.
	 * Includes tile-specific count matrices and all barcode reference arrays.
	 * Maintains matrix structure while zeroing accumulated counts for fresh analysis.
	 */
	@Override
	public void clearCounts(){
		writelock();
		super.clearCounts();
		if(leftCodes!=null){for(Barcode bc : leftCodes){if(bc!=null){bc.setCount(0);}}}
		if(rightCodes!=null){for(Barcode bc : rightCodes){if(bc!=null){bc.setCount(0);}}}
		if(allCodes!=null){for(Barcode bc : allCodes){if(bc!=null){bc.setCount(0);}}}
		for(long[][][] tcounts : tileCounts){ //Reset all tile-specific count matrices
			if(tcounts!=null){Tools.fill(tcounts, 0);}
		}
		writeunlock();
	}
	
	/**
	 * Computes weighted average of two probability matrices, storing result in the first matrix.
	 * Blends tile-specific probability matrix with global probability matrix using specified weight.
	 * All probability values are clamped to valid range [0,1] after computation.
	 * @param a First probability matrix (receives the weighted average result)
	 * @param b Second probability matrix to blend with first
	 * @param weight Relative weight of matrix a in the average (higher values favor matrix a)
	 */
	public void weightedAverage(float[][][] a, float[][][] b, float weight){
		writelock();
//assert(a[0][0][0]!=0 && a[0][0][0]!=1) : toBytesProb(a, null);
//assert(b[0][0][0]!=0 && b[0][0][0]!=1) : toBytesProb(b, null);
		float mult=1f/(1f+weight); //Normalization factor for weighted average
		for(int i=0; i<a.length; i++){
			for(int j=0; j<a[i].length; j++){
				for(int k=0; k<a[i][j].length; k++){
					a[i][j][k]=Tools.mid(0, (a[i][j][k]*weight+b[i][j][k])*mult, 1); //Weighted blend clamped to [0,1]
				}
			}
		}
		writeunlock();
	}
	
	/**
	 * Generates tile-specific probability matrices from accumulated count data.
	 * Converts count matrices to probability matrices and blends with global probabilities.
	 * Clears probability matrices for tiles with no count data to conserve memory.
	 */
	void makeTileProbs(){
		writelock();
		for(int tile=0; tile<tileCounts.size(); tile++){
			long[][][] tcounts=tileCounts.get(tile);
			if(tcounts!=null){ //Tile has accumulated count data
				float[][][] tprobs=getProbsForTile(tile, true);
				fillProbs(tcounts, tprobs); //Convert counts to probabilities
				weightedAverage(tprobs, probs, TILE_WEIGHT); //Blend tile-specific with global probabilities
				//TODO: We can add in the flowcell average here
			}else{ //No data for this tile
				if(tileProbs.size()>tile){tileProbs.set(tile, null);} //Clear unused probability matrix
			}
		}
		writeunlock();
	}

	/*--------------------------------------------------------------*/
	/*----------------          Populating          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Serializes count matrices to text format for storage or debugging.
	 * Includes both global count matrix and all tile-specific count matrices with tile identifiers.
	 * @param bb ByteBuilder to append formatted output (created if null)
	 * @return ByteBuilder containing formatted count matrix data
	 */
	public ByteBuilder toBytes(ByteBuilder bb){
		if(bb==null){bb=new ByteBuilder();}
		toBytes(counts, bb); //Serialize global count matrix
		for(int tile=0; tile<tileCounts.size(); tile++){ //Serialize each tile's count matrix
			long[][][] tcounts=tileCounts.get(tile);
			if(tcounts!=null){
				bb.nl().append("#Tile ").append(tile).nl();
				toBytes(tcounts, bb);
			}
		}
		return bb;
	}
	
	/**
	 * Serializes probability matrices to text format for analysis or debugging.
	 * Includes both global probability matrix and all tile-specific probability matrices with tile identifiers.
	 * @param bb ByteBuilder to append formatted output (created if null)
	 * @return ByteBuilder containing formatted probability matrix data
	 */
	public ByteBuilder toBytesProb(ByteBuilder bb){
		if(bb==null){bb=new ByteBuilder();}
		toBytesProb(probs, bb); //Serialize global probability matrix
		for(int tile=0; tile<tileProbs.size(); tile++){ //Serialize each tile's probability matrix
			float[][][] tprobs=tileProbs.get(tile);
			if(tprobs!=null){
				bb.nl().append("#Tile ").append(tile).nl();
				toBytesProb(tprobs, bb);
			}
		}
		return bb;
	}
	
	/**
	 * Static utility method to format probability matrix data as tab-delimited text.
	 * Creates header row and formats each position-call-reference combination with probabilities.
	 * @param probs Three-dimensional probability matrix [position][query_base][reference_base]
	 * @param bb ByteBuilder to append formatted output (created if null)
	 * @return ByteBuilder containing tab-delimited probability matrix data
	 */
	private static ByteBuilder toBytesProb(float[][][] probs, ByteBuilder bb){
		if(bb==null){bb=new ByteBuilder();}
		bb.append("#pos\tcall\tA\tC\tG\tT\n"); //Header row
		for(int pos=0; pos<probs.length; pos++){ //Each barcode position
			for(int xq=0; xq<5; xq++){ //Each query base type (A,C,G,T,N)
				final byte q=numberToBase[xq];
				bb.append(pos).tab().append(q);
				for(int xr=0; xr<probs[pos][xq].length; xr++){ //Each reference base type
					final float prob=probs[pos][xq][xr];
					bb.tab().append(prob,4); //4 decimal places for precision
				}
				bb.nl();
			}
		}
		return bb;
	}
	
	/**
	 * Prints assignment mapping results to file with probability scores and count information.
	 * Creates MapLine objects for sorting and formatting, then delegates to inherited print method.
	 * Computes probabilistic scores for each assignment using tile-specific matrices.
	 * @param assignmentMap HashMap mapping query sequences to reference barcode names
	 * @param mapOut Output file path for assignment results
	 * @param counts HashMap of query sequence counts (may be null)
	 * @param overwrite Whether to overwrite existing output file
	 * @param append Whether to append to existing output file
	 */
	@Override
	public void printAssignmentMap(HashMap<String, String> assignmentMap,
			String mapOut, HashMap<String, Barcode> counts, boolean overwrite, boolean append){
		ArrayList<MapLine> lines=new ArrayList<MapLine>();
		for(Entry<String, String> e : assignmentMap.entrySet()){
			String a=e.getKey(), b=e.getValue();
			Barcode bc=getBarcode(b);
			double pscore=scoreUsingProbs(a.getBytes(), bc.name.getBytes(), 0, bc.tile); //Compute assignment confidence
			Barcode v=(counts==null ? null : counts.get(a));
			lines.add(new MapLine(a, b, v==null ? 0 : v.count(), (float)pscore));
		}
		Collections.sort(lines); //Sort by score for better output organization
		printAssignmentMap(lines, mapOut, overwrite, append);
	}
	
	/**
	 * Adds alignment count data to tile-specific count matrix for a query-reference pair.
	 * Updates position-specific mismatch statistics and global assignment counters.
	 * Uses 'N' as reference for unassigned queries to track error patterns.
	 * @param query Query barcode sequence
	 * @param ref Reference barcode (null for unassigned queries)
	 * @param count Number of observations for this query-reference pair
	 * @param tile Tile identifier for position-specific count matrix
	 */
	public void addToTile(String query, Barcode ref, long count, int tile){
		long[][][] countsT=getCountsForTile(tile, true);
		assert(ref==null || ref.length()==countsT.length);
		for(int i=0; i<query.length(); i++){ //Position-by-position alignment
			final int q=query.charAt(i), r=(ref==null ? 'N' : ref.charAt(i));
			final byte xq=baseToNumber[q], xr=baseToNumber[r]; //Convert bases to matrix indices
			countsT[i][xq][xr]+=count; //Accumulate mismatch count at this position
		}
		totalCounted+=count;
		if(ref!=null){ //Successfully assigned query
			ref.incrementSync(count); //Thread-safe increment of reference barcode count
			totalAssigned+=count;
			totalAssignedToExpected+=ref.expected*count; //Track assignments to expected vs chimeric references
		}
	}
	
	/**
	 * Returns true indicating this class supports tile-based processing.
	 * Enables tile-specific probability matrices and error modeling.
	 * @return Always returns true for tile-based matrix implementations
	 */
	@Override
	public boolean byTile(){return true;}
	
	/*--------------------------------------------------------------*/

	/**
	 * Worker thread for parallel barcode assignment processing.
	 * Each thread processes a subset of query barcodes using round-robin distribution.
	 * Maintains thread-local count matrices to avoid synchronization overhead during processing.
	 */
	class PopThread extends Thread{

		/**
		 * Constructs a worker thread with assignment parameters and work distribution settings.
		 * Creates thread-local assignment map if needed to avoid contention during processing.
		 * @param list_ Complete list of query barcodes to be distributed among threads
		 * @param maxHDist_ Maximum allowed Hamming distance for matches
		 * @param minRatio_ Minimum ratio between best and second-best scores
		 * @param minProb_ Minimum probability threshold for matches
		 * @param minCount_ Minimum count threshold for strict matching criteria
		 * @param map_ Shared assignment map (null if not needed)
		 * @param tid_ Thread identifier for round-robin work distribution
		 * @param threads_ Total number of worker threads
		 */
		public PopThread(ArrayList<Barcode> list_,
				int maxHDist_, float minRatio_, float minProb_, long minCount_,
				HashMap<String, String> map_, int tid_, int threads_){
			list=list_;
			maxHDist=maxHDist_;
			minRatio=minRatio_;
			minProb=minProb_;
			minCount=minCount_;
			tid=tid_;
			threads=threads_;
			map=(map_==null ? null : localCounts ? new HashMap<String, String>() : map_); //Create local map if using local counts
		}

		/**
		 * Main thread execution method that processes assigned query barcodes.
		 * Uses round-robin distribution to balance workload across threads.
		 * Applies relaxed thresholds for low-count queries to improve assignment rates.
		 */
		@Override
		public void run(){
			readlock();
			//It's possible that this unsynchronized list access is a source of nondeterminism
			for(int i=tid; i<list.size(); i+=threads){ //This could use an atomic to smooth the load.
				Barcode query=list.get(i);
				final String s=query.name;
				assert(s.length()==counts.length);
				final Barcode ref;
				if(query.count()>=minCount){ //High-count queries use strict thresholds
					ref=findClosest(s, maxHDist, minRatio, minProb, query.tile);
				}else{ //Low-count queries use relaxed thresholds
					ref=findClosest(s, maxHDist, minRatio*4, minProb*8, query.tile);
				}
				assert(localCounts);

				addT(s, ref, query.count(), query.tile); //Update thread-local count matrix
				if(map!=null && ref!=null && ref.expected==1){map.put(s+query.tile, ref.name);} //Record expected assignments
			}
			readunlock();
		}
		
		/**
		 * Thread-local version of addToTile that updates thread-specific count matrices.
		 * Avoids synchronization during processing by using thread-local data structures.
		 * @param query Query barcode sequence
		 * @param ref Reference barcode (null for unassigned queries)
		 * @param count Number of observations for this query-reference pair
		 * @param tile Tile identifier for position-specific count matrix
		 */
		public void addT(String query, Barcode ref, long count, int tile){
			long[][][] countsT=getCountsForTileT(tile);
			assert(ref==null || ref.length()==countsT.length);
			for(int i=0; i<query.length(); i++){ //Position-by-position alignment
				final int q=query.charAt(i), r=(ref==null ? 'N' : ref.charAt(i));
				final byte xq=baseToNumber[q], xr=baseToNumber[r]; //Convert bases to matrix indices
				countsT[i][xq][xr]+=count; //Accumulate in thread-local matrix
			}
			totalCountedT+=count; //Update thread-local counters
			if(ref!=null){ //Successfully assigned query
				ref.incrementSync(count); //Thread-safe increment (shared data)
				totalAssignedT+=count;
				totalAssignedToExpectedT+=ref.expected*count;
			}
		}
		
		/**
		 * Retrieves or creates thread-local count matrix for specified tile.
		 * Lazily allocates count matrices to conserve memory for unused tiles.
		 * @param tile Tile identifier (must be > 0)
		 * @return Thread-local count matrix for the specified tile
		 */
		long[][][] getCountsForTileT(int tile){
			assert(tile>0) : tile;
			while(tileCountsT.size()<=tile){tileCountsT.add(null);} //Expand list as needed
			if(tileCountsT.get(tile)==null){tileCountsT.set(tile, new long[length][5][5]);} //Lazy allocation
			return tileCountsT.get(tile);
		}

		/** Complete list of query barcodes distributed among all worker threads */
		final ArrayList<Barcode> list;
		/** Maximum allowed Hamming distance for barcode matches */
		final int maxHDist;
		/** Minimum ratio between best and second-best scores to avoid ambiguous matches */
		final float minRatio;
		/** Minimum probability threshold for accepting matches */
		final float minProb;
		/** Minimum count threshold for applying strict matching criteria */
		final float minCount;
		/** Thread identifier for round-robin work distribution */
		final int tid;
		/** Total number of worker threads in the thread pool */
		final int threads;
		/** Assignment map for storing query-to-reference mappings (thread-local or shared) */
		final HashMap<String, String> map; //May need modification...

		/** Thread-local tile-specific count matrices to avoid synchronization overhead */
		final ArrayList<long[][][]> tileCountsT=new ArrayList<long[][][]>();
		/** Thread-local counter for total queries processed */
		long totalCountedT;
		/** Thread-local counter for successfully assigned queries */
		long totalAssignedT;
		/** Thread-local counter for queries assigned to expected (non-chimeric) references */
		long totalAssignedToExpectedT;
	}

	/**
	 * Accumulates results from completed worker thread into global data structures.
	 * Merges thread-local count matrices and counters with global totals.
	 * Called by ThreadWaiter framework after thread completion.
	 * @param t Completed PopThread with results to accumulate
	 */
	@Override
	public void accumulate(PopThread t){
		writelock();
		if(localCounts){ //Thread used local count matrices
			synchronized(t){
				for(int i=0; i<t.tileCountsT.size(); i++){ //Merge each tile's count matrix
					long[][][] countsT=t.tileCountsT.get(i);
					if(countsT!=null){
						long[][][] counts2=getCountsForTile(i, true); //Get or create tile-specific matrix
						Tools.add(counts2, countsT); //Merge into tile-specific global matrix
						Tools.add(counts, countsT); //Merge into overall global matrix
					}
				}
				totalCounted+=t.totalCountedT; //Accumulate thread-local counters
				totalAssigned+=t.totalAssignedT;
				totalAssignedToExpected+=t.totalAssignedToExpectedT;
			}
		}
		writeunlock();
	}

	/**
	 * Returns success status for ThreadWaiter framework.
	 * Currently always returns false - success determination handled elsewhere.
	 * @return Always returns false (success tracking not implemented via this method)
	 */
	@Override
	public boolean success(){
		//TODO Auto-generated method stub
		return false;
	}
	
	/*--------------------------------------------------------------*/
	
	/**
	 * Retrieves or optionally creates global count matrix for specified tile.
	 * Manages tile-specific count matrices with lazy allocation to conserve memory.
	 * @param tile Tile identifier (must be > 0)
	 * @param createIfAbsent Whether to create matrix if it doesn't exist
	 * @return Count matrix for specified tile, or null if absent and not created
	 */
	private long[][][] getCountsForTile(int tile, boolean createIfAbsent){
		assert(tile>0) : tile;
		if(!createIfAbsent){return tileCounts.size()<tile ? null : tileCounts.get(tile);}
		while(tileCounts.size()<=tile){tileCounts.add(null);} //Expand list to accommodate tile index
		if(tileCounts.get(tile)==null){tileCounts.set(tile, new long[length][5][5]);} //Lazy allocation: [position][query_base][ref_base]
		return tileCounts.get(tile);
	}
	
	/**
	 * Retrieves or optionally creates global probability matrix for specified tile.
	 * Manages tile-specific probability matrices with lazy allocation to conserve memory.
	 * @param tile Tile identifier (must be > 0)
	 * @param createIfAbsent Whether to create matrix if it doesn't exist
	 * @return Probability matrix for specified tile, or null if absent and not created
	 */
	private float[][][] getProbsForTile(int tile, boolean createIfAbsent){
		assert(tile>0) : tile;
		if(!createIfAbsent){return tileProbs.size()<tile ? null : tileProbs.get(tile);}
		while(tileProbs.size()<=tile){tileProbs.add(null);} //Expand list to accommodate tile index
		if(tileProbs.get(tile)==null){tileProbs.set(tile, new float[length][5][4]);} //Lazy allocation: [position][query_base][ref_base] (4 ref bases)
		return tileProbs.get(tile);
	}

	/** List of tile-specific count matrices indexed by tile ID */
	private final ArrayList<long[][][]> tileCounts=new ArrayList<long[][][]>();
	/** List of tile-specific probability matrices indexed by tile ID */
	private final ArrayList<float[][][]> tileProbs=new ArrayList<float[][][]>();
	
	/** Mapping between barcode instances for tile-specific processing (currently unused) */
	HashMap<Barcode, Barcode> tileMap=new HashMap<Barcode, Barcode>();
}
