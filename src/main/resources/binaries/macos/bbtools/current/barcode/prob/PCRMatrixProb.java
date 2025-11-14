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
 * Tracks data about bar code mismatches by position.
 * Uses split barcodes instead of contiguous.
 * 
 * This class is closed-source.
 * It is not legal to redistribute, replicate, or reverse-engineer.
 * 
 * @author Brian Bushnell
 * @date March 25, 2024
 *
 */
public class PCRMatrixProb extends PCRMatrixProbAbstract implements Accumulator<PCRMatrixProb.PopThread> {

	/*--------------------------------------------------------------*/
	/*----------------         Constructor          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Constructs a PCRMatrixProb for probabilistic barcode matching.
	 * Uses position-specific error probability models to identify the most
	 * likely expected barcode corresponding to observed sequences with errors.
	 * Supports both single barcodes and dual split barcodes with a delimiter.
	 * @param length1_ Length of first barcode segment 
	 * @param length2_ Length of second barcode segment (0 for single barcodes)
	 * @param delimiter_ Position of delimiter between barcode segments
	 * @param hdistSum_ Whether to sum Hamming distances across segments
	 */
	public PCRMatrixProb(int length1_, int length2_, int delimiter_, boolean hdistSum_){
		super(length1_, length2_, delimiter_, hdistSum_, false);
	}

	/*--------------------------------------------------------------*/
	/*----------------           Parsing            ----------------*/
	/*--------------------------------------------------------------*/

	/*--------------------------------------------------------------*/
	/*----------------         Probability          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Finds the most probable expected barcode matching the query sequence.
	 * Routes to either single or dual barcode matching based on barcode configuration.
	 * Uses default thresholds for maximum Hamming distance, minimum probability ratio, and minimum probability.
	 * @param s Query barcode sequence to match
	 * @return Best matching expected barcode, or null if no acceptable match found
	 */
	@Override
	public Barcode findClosest(String s){
		return length2<1 ? findClosestSingle(s, maxHDist1, minRatio1, minProb1) : //Single barcode mode
			findClosestDual(s, maxHDist1, minRatio1, minProb1); //Split barcode mode
	}
	
	/**
	 * Finds the most probable expected barcode matching the query sequence.
	 * Routes to either single or dual barcode matching based on barcode configuration.
	 * Applies custom thresholds for filtering matches by quality and specificity.
	 * @param s Query barcode sequence to match
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode
	 * @param minRatio Minimum ratio between best and second-best probability scores
	 * @param minProb Minimum probability threshold for accepting a match
	 * @return Best matching expected barcode, or null if no acceptable match found
	 */
	public Barcode findClosest(String s, int maxHDist, float minRatio, float minProb){
		return length2<1 ? findClosestSingle(s, maxHDist, minRatio, minProb) : //Single barcode mode
			findClosestDual(s, maxHDist, minRatio, minProb); //Split barcode mode
	}
	
	/**
	 * Finds the best matching expected barcode for single (non-split) barcode sequences.
	 * Uses position-specific error probabilities to score candidates and identify the most
	 * likely match. Implements hybrid mode for fast exact matching before probability calculation.
	 * Algorithm calculates probability scores for all expected barcodes and selects the best
	 * one that meets Hamming distance, probability ratio, and minimum probability thresholds.
	 * @param q Query barcode sequence to match
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode
	 * @param minRatio Minimum ratio between best and second-best probability scores
	 * @param minProb Minimum probability threshold for accepting a match
	 * @return Best matching expected barcode, or null if no acceptable match found
	 */
	private Barcode findClosestSingle(String q, int maxHDist, float minRatio, float minProb){
		if(hybridHDist>=0){
			//This forces perfect matches to return self.
			Barcode x=allCodesMap.get(q); //Fast exact match lookup
			if(x!=null && x.expected==1){return x;} //Return perfect expected matches immediately
			
			if(hybridHDist>0){
				x=findClosestSingleHDist(q, hybridHDist, hybridClearzone); //Fast Hamming distance matching
				if(x!=null){return x;}
			}
		}
		assert(q.length()==length1);
		final byte[] left=q.getBytes();
//		DoubleList plist=getDoubleList(0);
		DoubleList plist=new DoubleList(leftBytes.length);
		fillProbList(left, leftBytes, 0, plist); //Calculate probability scores for all expected barcodes
		double sum=0;
		double score=0;
		assert(allCodes[0]==leftCodes[0]);
		assert(allCodes.length==leftCodes.length);
		int bestIdx=-1;
		for(int i=0; i<leftBytes.length; i++){
			double raw=plist.get(i);
			double product=raw*allCodes[i].frequency; //Weight by barcode frequency
			if(product>score && hdist(allCodes[i], q)<=maxHDist){
				sum+=(product>score ? score : product); //Accumulate previous best for ratio test
				bestIdx=(product>score ? i : bestIdx);
				score=(product>score ? product : score);
			}
		}

		assert(score<=1);
		assert(sum>=0);
		if(bestIdx<0){return null;}
		if(sum*minRatio>=score){return null;} //Check specificity - reject ambiguous matches
		Barcode best=allCodes[bestIdx];
		int hdist=hdist(best, q);
		if(hdist>maxHDist){return null;}
		
		if(FBRANCH==0){ //Frequency-weighted probability threshold
			if(score*best.frequency<minProb){return null;}
		}else if(FBRANCH==1){ //Raw probability threshold only
			if(score<minProb){return null;}
		}else if(FBRANCH==2){ //Enhanced frequency weighting with dual thresholds
			if(score*best.frequency*20<minProb){return null;} //Enhanced frequency weighting
			if(score<minProb){return null;}
		}else{
			throw new RuntimeException("Unknown FBRANCH");
		}
		
		return best;
	}
	
	/**
	 * Finds the best matching expected barcode for dual (split) barcode sequences.
	 * Handles barcodes with two segments separated by a delimiter, calculating probability
	 * scores independently for left and right segments then combining them. Uses position-specific
	 * error models for both segments and applies Hamming distance constraints to each.
	 * Algorithm evaluates all expected barcode combinations and selects the best scoring
	 * candidate that meets all quality thresholds.
	 * @param q Query barcode sequence to match (format: leftSegment+delimiter+rightSegment)
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode per segment
	 * @param minRatio Minimum ratio between best and second-best probability scores
	 * @param minProb Minimum probability threshold for accepting a match
	 * @return Best matching expected barcode, or null if no acceptable match found
	 */
	private Barcode findClosestDual(String q, int maxHDist, float minRatio, float minProb){
		if(hybridHDist>=0){
			//This forces perfect matches to return self.
			Barcode x=allCodesMap.get(q);
			if(x!=null && x.expected==1){return x;} //Probably doesn't need to be expected
			
			if(hybridHDist>0){
				x=findClosestDualHDist(q, hybridHDist, hybridClearzone);
				if(x!=null){return x;}
			}
		}
//		assert(!"TCGAATGATC+CGCATGTCGT".equals(q));
//		assert(false) : q;
		//if(verbose) {System.err.println("Looking for "+q);}
		final byte[] left=new byte[length1];
		final byte[] right=new byte[length2];
		for(int i=0; i<length1; i++){left[i]=(byte) q.charAt(i);} //Extract left segment
		for(int i=length2-1, j=q.length()-1; i>=0; i--, j--){ //Extract right segment from query end
			right[i]=(byte) q.charAt(j);
		}

		//ThreadLocals caused a 100x speed decrease on Perlmutter
//		final DoubleList llist=getDoubleList(0);
//		final DoubleList rlist=getDoubleList(1);
//		final IntList hdrlist=getIntList();
		final DoubleList llist=new DoubleList(leftBytes.length);
		final DoubleList rlist=new DoubleList(rightBytes.length);
		final IntList hdrlist=new IntList(rightBytes.length);
		fillProbList(left, leftBytes, 0, llist); //Calculate left segment probabilities
		fillProbList(right, rightBytes, start2, rlist); //Calculate right segment probabilities
		fillHDistList(right, rightBytes, hdrlist); //Precompute right segment Hamming distances
		double sum=0;
		double score=0;
		int hdist=letters;
		int bestIdx=-1;

		//It's possible to fill an hdistRight list here.
		for(int i=0, idx=0; i<leftBytes.length; i++){ //Iterate through all left barcode candidates
			final int hdleft=hdist(leftBytes[i], left);
			final double raw1=llist.get(i);
			for(int j=0; j<rightBytes.length; j++, idx++){ //Iterate through all right barcode candidates
				final int hdright=hdrlist.get(j);
				final double raw2=rlist.get(j);
				final double product=raw1*raw2*comboFrequency[idx]; //Combined probability score
				sum+=(product>score ? score : product); //Accumulate previous best for ratio test
				if(product>score && hdleft<=maxHDist && hdright<=maxHDist){ //Check if best and within distance limits
					bestIdx=(product>score ? idx : bestIdx);
					score=(product>score ? product : score);
					
//					assert(!"TCGAATGATT+CGCATGTCGT".equals(q) || allCodes[bestIdx].hdist(q)<=6) : 
//						"\n"+q+", "+allCodes[bestIdx].name+", hd="+allCodes[bestIdx].hdist(q)+", score="+
//						(float)score+", hdl="+hdleft+", hdr="+hdist(rightBytes[j], right)+", hdm="+maxHDist;
				}
			}
		}
		
		if(bestIdx<0){return null;}
		
		assert(score<=1);
		assert(sum>=0);
		final Barcode best=allCodes[bestIdx];
		final boolean perfect=(best.expected==1 && q.equals(best.name));
		final float cf=comboFrequency[bestIdx];
//		final double score0=score;
		if(perfect){ //Boost score for perfect expected matches
//			assert(!q.equals(best.name)) : "\n"+best.name+", "+q+", "+best.expected+", "
//					+score+", "+hdist(best, q)+", "+q.equals(best.name)+"\n";
//			double f=score;
			score=Tools.max(score, (score+minExpectedScore)*0.5); //Weighted average with minimum expected score
//			assert(false) : "\n"+best.name+", "+q+", "
//				+f+"->"+score+", "+hdist(best, q)
//				+"\ncombo="+comboFrequency[bestIdx]+", raw="+f/comboFrequency[bestIdx];
		}
		final float mr=(perfect ? Tools.min(minRatio, expectedRatio1) : minRatio);
//		assert(!"TCGAATGATT+CGCATGTCGT".equals(q) || perfect) : 
//			"\n"+q+", "+best.name+", hd="+hdist(best, q)+", score="+
//			(float)score+", score0="+score0+", raw="+(score0/cf)+
//			", cf="+cf+", sum="+(float)sum+", sum*mr="+((float)sum*mr);
		if(sum*mr>=score){ //Check specificity via ratio test
			//This unexpectedly fired once for some reason...
			//May have had something to do with lots of poly-Gs and turning off hybrid mode.
//			assert(!perfect) : "\n"+q+", "+best.name+", "+hdist(best, q)+", "+
//					(float)score+", "+cf+", "+(float)sum+", "+((float)sum*mr);
			return null;
		}
		if(FBRANCH==0){
			if(score*best.frequency<minProb){
				assert(!perfect) : "\n"+q+", "+best.name+", "+hdist(best, q)+", "+score+", "+cf;
				return null;
			}
		}else if(FBRANCH==1){
			if(score<minProb){
				assert(!perfect) : "\n"+q+", "+best.name+", "+hdist(best, q)+", "+score+", "+cf;
				return null;
			}
			//Works OK with "minratio0=4 minratio1=200k minprob0=-12 minprob1=-4.8 fbranch=1"
		}else if(FBRANCH==2){
			if(score*best.frequency*20<minProb){return null;} //Enhanced frequency weighting
			if(score<minProb){return null;}
		}else{
			throw new RuntimeException("Unknown FBRANCH");
		}
		hdist=hdist(best, q);
		if(hdist>maxHDist){
			assert(!perfect) : "\n"+q+", "+best.name+", "+hdist(best, q)+", "+score+", "+cf;
			return null;
		}
		
		return best;
	}
	
	/**
	 * Calculates probability scores for a query sequence against all expected barcodes.
	 * Uses position-specific error probability models to score each expected barcode
	 * candidate against the query sequence. Each position contributes a probability
	 * based on the observed error rates from training data.
	 * @param q Query sequence as byte array
	 * @param list Array of expected barcode sequences to score against
	 * @param pos Starting position offset for scoring (used with split barcodes)
	 * @param results DoubleList to store probability scores for each expected barcode
	 */
	public void fillProbList(byte[] q, byte[][] list, int pos, DoubleList results){
		results.clear();
		assert(!expectedList.isEmpty());
		for(int i=0; i<list.length; i++){
			byte[] ref=list[i];
			double s=scoreUsingProbs(q, ref, pos); //Calculate probability score using error model
			results.add(s);
		}
	}
	
	/**
	 * Creates assignment mapping from observed barcodes to expected barcodes.
	 * Processes a collection of observed barcode counts and attempts to assign each
	 * to its most likely expected barcode using probability-based matching.
	 * Automatically chooses single-threaded or multi-threaded processing based on
	 * dataset size and system configuration. Low-count barcodes use relaxed thresholds.
	 * @param codeCounts Collection of observed barcode sequences with counts
	 * @param minCountA Minimum count threshold for standard assignment parameters  
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode
	 * @param minRatio Minimum ratio between best and second-best probability scores
	 * @param minProb Minimum probability threshold for accepting a match
	 * @return HashMap mapping observed barcode strings to expected barcode strings
	 */
	public HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCountA,
			int maxHDist, float minRatio, float minProb){
		writelock();
		Timer t=new Timer();
		assert(expectedList!=null && expectedList.size()>0) : expectedList;
		assert(codeCounts!=null);
		ArrayList<Barcode> list=highpass(codeCounts, 0); //Currently zero so I can use different logic
		HashMap<String, String> map=new HashMap<String, String>(Tools.max(200, list.size()/10));
		totalCounted=totalAssigned=totalAssignedToExpected=0;
		final long ops=list.size()*(long)expectedList.size();
		//TODO: Why list.size()<2 ?  2 is way too low; should be like 16 or 1000.
		final int threads=Tools.min(Shared.threads(), matrixThreads);
		if(list.size()<2 || ops<100000 || threads<2){ //Singlethreaded mode
			for(Barcode query : list){
				//TODO: I could simply change the cutoffs when the count is below mincount
				final String s=query.name;
				assert(s.length()==counts.length);
				final Barcode ref;
				if(query.count()>=minCountA){
					ref=findClosest(s, maxHDist, minRatio, minProb);
				}else{
					ref=findClosest(s, maxHDist, minRatio*4, minProb*8); //Relaxed thresholds for low counts
				}
				final long count=query.count();
				totalCounted+=count;
				if(ref!=null){
					totalAssigned+=count;
					if(ref.expected==1){
						totalAssignedToExpected+=count;
						map.put(s, ref.name);
					}
				}
			}
		}else{ //Multi-threaded mode for large datasets
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
	 * Single-threaded population of barcode assignment counts.
	 * Processes each observed barcode sequentially to find its best expected match
	 * and updates the error model counts matrix. Uses relaxed thresholds for
	 * low-count barcodes to improve sensitivity.
	 * @param countList List of observed barcodes to process
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode
	 * @param minRatio Minimum ratio between best and second-best probability scores  
	 * @param minProb Minimum probability threshold for accepting a match
	 * @param minCount Minimum count threshold for standard assignment parameters
	 */
	void populateCountsST(ArrayList<Barcode> countList,
			int maxHDist, float minRatio, float minProb, long minCount){
		writelock();
		for(Barcode query : countList){
			final String s=query.name;
			assert(s.length()==counts.length);
			final Barcode ref;
			if(query.count()>=minCount){
				ref=findClosest(s, maxHDist, minRatio, minProb);
			}else{
				ref=findClosest(s, maxHDist, minRatio*4, minProb*8); //More permissive matching for rare barcodes
			}
			add(s, ref, query.count());
		}
		writeunlock();
	}

	/**
	 * Multi-threaded population of barcode assignment counts.
	 * Distributes barcode processing across multiple worker threads for improved
	 * performance on large datasets. Creates PopThread workers that process
	 * either split sublists or shared list with strided access patterns.
	 * Uses ThreadWaiter coordination pattern for thread management.
	 * @param list List of observed barcodes to process
	 * @param maxHDist Maximum allowed Hamming distance from expected barcode
	 * @param minRatio Minimum ratio between best and second-best probability scores
	 * @param minProb Minimum probability threshold for accepting a match  
	 * @param minCount Minimum count threshold for standard assignment parameters
	 * @param map Shared assignment map for expected barcode mappings
	 */
	void populateCountsMT(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb, long minCount,
			HashMap<String, String> map){
		//Do anything necessary prior to processing
		
		//Determine how many threads may be used
		final int threads=Tools.mid(1, Tools.min(matrixThreads, Shared.threads()), list.size()/8);
		
		//Fill a list with PopThreads
		ArrayList<PopThread> alpt=new ArrayList<PopThread>(threads);
		if(splitList){ //Not needed for speed or synchronization; can be removed
			ArrayList<Barcode>[] lists=splitList(list, threads);
			for(int i=0; i<threads; i++){
				alpt.add(new PopThread(lists[i], maxHDist, 
						minRatio, minProb, minCount, map, i, threads));
			}
		}else{
			for(int i=0; i<threads; i++){
				alpt.add(new PopThread(list, maxHDist, 
						minRatio, minProb, minCount, map, i, threads));
			}
		}
		
		//Start the threads and wait for them to finish
		boolean success=ThreadWaiter.startAndWait(alpt, this);
		errorState&=!success;
		
		//Do anything necessary after processing
	}
	
	/**
	 * Divides a list of barcodes into sublists for multi-threaded processing.
	 * Distributes barcodes across thread sublists using round-robin assignment
	 * to ensure balanced workload distribution. Each sublist gets approximately
	 * equal numbers of barcodes for balanced parallel processing workload.
	 * @param list Original list of barcodes to divide
	 * @param threads Number of worker threads (and sublists to create)
	 * @return Array of barcode sublists, one per thread
	 */
	ArrayList<Barcode>[] splitList(ArrayList<Barcode> list, int threads){
		@SuppressWarnings("unchecked")
		ArrayList<Barcode>[] array=new ArrayList[threads];
		int len=(list.size()/threads)+1; //Pre-allocate with estimated capacity
		for(int i=0; i<threads; i++){array[i]=new ArrayList<Barcode>(len);}
		for(int i=0; i<list.size(); i++){array[i%threads].add(list.get(i));} //Round-robin assignment for load balancing
		return array;
	}
	
	/**
	 * Resets all barcode counts to zero.
	 * Clears counts in the parent class and also resets counts for all
	 * left segment, right segment, and combined barcode arrays. Used to
	 * prepare for a new round of barcode processing or analysis.
	 */
	public void clearCounts(){
		writelock();
		super.clearCounts();
		if(leftCodes!=null){for(Barcode bc : leftCodes){if(bc!=null){bc.setCount(0);}}}
		if(rightCodes!=null){for(Barcode bc : rightCodes){if(bc!=null){bc.setCount(0);}}}
		if(allCodes!=null){for(Barcode bc : allCodes){if(bc!=null){bc.setCount(0);}}}
		writeunlock();
	}

	/*--------------------------------------------------------------*/
	/*----------------          Populating          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Outputs the probability matrix as tab-delimited text format.
	 * Creates a table showing position-specific error probabilities for each
	 * query base call at each reference position. Format includes columns for
	 * position, query base call, and probabilities for each possible reference base.
	 * Used for examining the learned error model and debugging probability calculations.
	 * @param bb ByteBuilder to append output to (created if null)
	 * @return ByteBuilder containing formatted probability matrix data
	 */
	public ByteBuilder toBytesProb(ByteBuilder bb){
		if(bb==null){bb=new ByteBuilder();}
		bb.append("#pos\tcall\tA\tC\tG\tT\n");
		for(int pos=0; pos<counts.length; pos++){
			for(int xq=0; xq<5; xq++){
				final byte q=numberToBase[xq];
				bb.append(pos).tab().append(q);
				for(int xr=0; xr<probs[pos][xq].length; xr++){
//					final byte r=numberToBase[xr];
					final float prob=probs[pos][xq][xr];
					bb.tab().append(prob,4); //Append with 4 decimal precision
				}
				bb.nl();
			}
		}
		return bb;
	}
	
	/**
	 * Prints assignment mappings to output file with probability scores.
	 * Creates a sorted list of assignment mappings from observed to expected barcodes,
	 * includes probability scores calculated using the error model, and outputs
	 * in tab-delimited format. Each line contains observed barcode, expected barcode,
	 * count, and probability score for the assignment.
	 * @param assignmentMap Mappings from observed to expected barcode strings
	 * @param mapOut Output file path for assignment mappings
	 * @param counts Barcode count data for including observation counts
	 * @param overwrite Whether to overwrite existing output file
	 * @param append Whether to append to existing output file
	 */
	@Override
	public void printAssignmentMap(HashMap<String, String> assignmentMap,
			String mapOut, HashMap<String, Barcode> counts, boolean overwrite, boolean append){
		ArrayList<MapLine> lines=new ArrayList<MapLine>();
		for(Entry<String, String> e : assignmentMap.entrySet()){
			String a=e.getKey(), b=e.getValue();
			Barcode v=(counts==null ? null : counts.get(a));
			double pscore=scoreUsingProbs(a.getBytes(), b.getBytes(), 0); //Calculate probability score for assignment
			lines.add(new MapLine(a, b, v==null ? 0 : v.count(), (float)pscore));
		}
		Collections.sort(lines);
		printAssignmentMap(lines, mapOut, overwrite, append);
	}
	
	/**
	 * Indicates whether this implementation processes barcodes by sequencing tile.
	 * This probability-based implementation processes all barcodes uniformly
	 * regardless of their origin tile, so always returns false.
	 * @return false - this implementation does not use tile-based processing
	 */
	@Override
	public boolean byTile(){return false;}
	
	/*--------------------------------------------------------------*/

	/**
	 * Worker thread class for multi-threaded barcode assignment processing.
	 * Each PopThread processes a subset of observed barcodes, finding their best
	 * expected barcode matches using probability-based algorithms. Supports both
	 * split-list and strided access patterns for work distribution. Maintains
	 * thread-local counters that are accumulated after completion.
	 */
	class PopThread extends Thread {

		/**
		 * Constructs a PopThread worker for barcode assignment processing.
		 * @param list_ List of barcodes to process (shared or split sublist)
		 * @param maxHDist_ Maximum allowed Hamming distance from expected barcode
		 * @param minRatio_ Minimum ratio between best and second-best probability scores
		 * @param minProb_ Minimum probability threshold for accepting a match
		 * @param minCount_ Minimum count threshold for standard assignment parameters
		 * @param map_ Shared assignment map for expected barcode mappings
		 * @param tid_ Thread ID for strided access pattern
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
			sharedMap=map=map_;
		}

		/**
		 * Main thread execution method for processing barcode assignments.
		 * Initializes thread-local data structures, then processes assigned
		 * barcodes using either split-list (contiguous) or strided access patterns.
		 * Each barcode is synchronized during processing to prevent race conditions.
		 * Maintains thread-local counts that are accumulated by parent after completion.
		 */
		@Override
		public void run(){
			readlock();
			synchronized(this){
				if(localCounts){
					countsT=new long[length][5][5]; //Thread-local error model counts
					map=(map==null ? null : new HashMap<String, String>());
				}
				
				//TODO:  Access to list and query are unsynchronized here.
				
				final int start=(splitList ? 0 : tid); //Sublist starts at 0, strided starts at thread ID
				final int incr=(splitList ? 1 : threads); //Sublist increments by 1, strided by thread count
				for(int i=start; i<list.size(); i+=incr){
					Barcode query=list.get(i);
					synchronized(query){
						processQuery(query);
					}
				}
			}
			readunlock();
		}
		
		/**
		 * Processes a single barcode query to find its best expected match.
		 * Applies relaxed thresholds for low-count barcodes and updates either
		 * thread-local or synchronized shared data structures based on configuration.
		 * @param query Observed barcode to process and assign
		 */
		private void processQuery(Barcode query){
			final String s=query.name;
			assert(s.length()==counts.length);
			final Barcode ref;
			if(query.count()>=minCount){
				ref=findClosest(s, maxHDist, minRatio, minProb);
			}else{
				ref=findClosest(s, maxHDist, minRatio*4, minProb*8); //More permissive matching for rare barcodes
			}
			if(localCounts){ //TODO: Should be enforced
				addT(s, ref, query.count()); //Update thread-local counts
				if(map!=null && ref!=null && ref.expected==1){map.put(s, ref.name);}
			}else{
				synchronized(counts){ //Update shared counts with synchronization
					add(s, ref, query.count());
					if(map!=null && ref!=null && ref.expected==1){map.put(s, ref.name);}
				}
			}
		}
		
		/**
		 * Updates thread-local error model counts for a processed barcode assignment.
		 * Records position-specific error patterns by comparing query and reference
		 * sequences at each position. Maintains thread-local counters for total
		 * processed, assigned, and expected assignments that are accumulated later.
		 * @param query Observed barcode sequence
		 * @param ref Expected barcode reference (null if no assignment)
		 * @param count Observation count for this barcode
		 */
		public void addT(String query, Barcode ref, long count){
			assert(ref==null || ref.length()==countsT.length);
			for(int i=0; i<query.length(); i++){
				final int q=query.charAt(i), r=(ref==null ? 'N' : ref.charAt(i));
				final byte xq=baseToNumber[q], xr=baseToNumber[r];
				countsT[i][xq][xr]+=count; //Accumulate error patterns by position
			}
			totalCountedT+=count;
			if(ref!=null){
				//TODO: With localCounts I could make a copy of this to prevent need for sync (or use Atomics)
				//The speed seems fine though
				ref.incrementSync(count);
				totalAssignedT+=count;
				totalAssignedToExpectedT+=ref.expected*count;
			}
		}
		
		/** List of barcodes to process (shared or sublist) */
		final ArrayList<Barcode> list;
		/** Maximum allowed Hamming distance from expected barcode */
		final int maxHDist;
		/** Minimum ratio between best and second-best probability scores */
		final float minRatio;
		/** Minimum probability threshold for accepting a match */
		final float minProb;
		/** Minimum count threshold for standard assignment parameters */
		final float minCount;
		/** Thread ID for strided access pattern */
		final int tid;
		/** Total number of worker threads */
		final int threads;
		/** Shared assignment map reference */
		final HashMap<String, String> sharedMap;
		
		/** Thread-local assignment map */
		HashMap<String, String> map;
		/** Thread-local error model counts matrix */
		long[][][] countsT;
		
		/** Thread-local count of total processed barcodes */
		long totalCountedT;
		/** Thread-local count of successfully assigned barcodes */
		long totalAssignedT;
		/** Thread-local count of barcodes assigned to expected sequences */
		long totalAssignedToExpectedT;
	}

	/**
	 * Accumulates results from a completed PopThread worker into shared data structures.
	 * Combines thread-local error model counts with the main counts matrix and
	 * merges thread-local assignment maps. Uses nested synchronization to ensure
	 * thread-safe accumulation of all worker results. Only processes data when
	 * localCounts mode is enabled.
	 * @param t PopThread worker whose results should be accumulated
	 */
	@Override
	public void accumulate(PopThread t){
		writelock();
		if(localCounts){
			synchronized(counts){
				synchronized(t){
					synchronized(t.countsT){
						Tools.add(counts, t.countsT); //Accumulate thread results into shared matrix
						totalCounted+=t.totalCountedT;
						totalAssigned+=t.totalAssignedT;
						totalAssignedToExpected+=t.totalAssignedToExpectedT;
						if(t.map!=null){
							t.sharedMap.putAll(t.map); //Merge thread-local assignments
						}
					}
				}
			}
		}
		writeunlock();
	}

	/**
	 * Returns success status for the Accumulator interface.
	 * Currently always returns false as a placeholder implementation.
	 * @return false - success status not currently tracked
	 */
	@Override
	public boolean success(){
		//TODO Auto-generated method stub
		return false;
	}
	
	/*--------------------------------------------------------------*/
	
	/*--------------------------------------------------------------*/
	
	/*--------------------------------------------------------------*/
	
}
