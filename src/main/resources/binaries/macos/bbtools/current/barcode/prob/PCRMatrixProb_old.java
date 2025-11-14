package barcode.prob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;

import barcode.Barcode;
import barcode.PCRMatrix;
import fileIO.ByteStreamWriter;
import shared.Parse;
import shared.Shared;
import shared.Timer;
import shared.Tools;
import structures.ByteBuilder;
import template.Accumulator;
import template.ThreadWaiter;

/**
 * Tracks data about bar code mismatches by position.
 * Used for demultiplexing.
 * 
 * This class is closed-source.
 * It is not legal to redistribute, replicate, or reverse-engineer.
 * 
 * @author Brian Bushnell
 * @date March 22, 2024
 *
 */
public class PCRMatrixProb_old extends PCRMatrix implements Accumulator<PCRMatrixProb_old.PopThread> {

	/*--------------------------------------------------------------*/
	/*----------------         Constructor          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Creates a new PCRMatrixProb_old instance for probabilistic barcode matching.
	 * Initializes a 3D probability matrix to store P(observed_base|true_base) at each position.
	 * The probability matrix has dimensions [position][query_base][reference_base] where
	 * query_base can be A,C,G,T,N (5 states) and reference_base is A,C,G,T (4 states).
	 * @param length1_ Length of first barcode segment
	 * @param length2_ Length of second barcode segment
	 * @param delimiter_ Delimiter character between barcode segments
	 * @param hdistSum_ Whether to sum Hamming distances across segments
	 */
	public PCRMatrixProb_old(int length1_, int length2_, int delimiter_, boolean hdistSum_){
		super(length1_, length2_, delimiter_, hdistSum_);
		probs=new float[length][5][4]; //[position][call: A,C,G,T,N][ref: A,C,G,T]
	}

	/*--------------------------------------------------------------*/
	/*----------------           Parsing            ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Parses command-line arguments for PCRMatrixProb_old parameters.
	 * Handles refinement parameters (passes, distance thresholds, probability thresholds)
	 * and unexpected barcode frequency parameters. Supports both full parameter names
	 * and abbreviated forms for convenience.
	 * @param arg Complete argument string (unused but kept for interface compatibility)
	 * @param a Parameter name (case-insensitive for some parameters)
	 * @param b Parameter value as string
	 * @return true if parameter was recognized and parsed, false otherwise
	 */
	public static boolean parseStatic(String arg, String a, String b){
		
		if(a.equals("brrate") || a.equals("baserecombinationrate")){
			baseRecombinationRate=Float.parseFloat(b);
		}else if(a.equalsIgnoreCase("minUnexpectedFrequency0") || a.equals("minunfreq0")){
			minUnexpectedFrequency0=Float.parseFloat(b);
			if(minUnexpectedFrequency0<0){minUnexpectedFrequency0=(float)Math.pow(10, minUnexpectedFrequency0);} //Convert log10 values
		}else if(a.equalsIgnoreCase("minUnexpectedFrequency") || a.equals("mininfreq")){
			minUnexpectedFrequency=Float.parseFloat(b);
			if(minUnexpectedFrequency<0){minUnexpectedFrequency=(float)Math.pow(10, minUnexpectedFrequency);} //Convert log10 values
		}else if(a.equalsIgnoreCase("spoofReads") || a.equals("spoof")){
			spoofReads=Integer.parseInt(b);
		}
		
		else if(a.equals("passes") || a.equals("refinepasses") || a.equals("pcrpasses")){
			passes0=Integer.parseInt(b);
		}else if(a.equals("maxhdist0") || a.equals("hdist0")){
			maxHDist0=Integer.parseInt(b);
		}else if(a.equals("maxhdist") || a.equals("hdist") || a.equals("maxhdist1") || a.equals("hdist1")){
			maxHDist1=Integer.parseInt(b);
		}else if(a.equals("minratio0") || a.equals("ratio0")){
			minRatio0=(float)Parse.parseDoubleKMG(b);
			assert(minRatio0>=0) : minRatio0;
		}else if(a.equals("minratio1") || a.equals("ratio1") || a.equals("ratio") || a.equals("minratio")){
			minRatio1=(float)Parse.parseDoubleKMG(b);
			assert(minRatio1>=0) : minRatio1;
		}else if(a.equals("minprob0")){
			minProb0=(float)Parse.parseDoubleKMG(b);
			minProb0=(minProb0>=0 ? minProb0 : (float)Math.pow(10, minProb0)); //Handle log10 input
			assert(minProb0<=1 && minProb0>=0) : minProb0;
		}else if(a.equals("minprob1") || a.equals("minprob")){
			minProb1=(float)Parse.parseDoubleKMG(b);
			minProb1=(minProb1>=0 ? minProb1 : (float)Math.pow(10, minProb1)); //Handle log10 input
			assert(minProb1<=1 && minProb1>=0) : minProb1;
		}else{
			return false;
		}
		return true;
	}

	/**
	 * Instance-level parameter parsing method.
	 * Currently returns false for all parameters, deferring to static parsing.
	 * @param arg Complete argument string
	 * @param a Parameter name
	 * @param b Parameter value
	 * @return Always returns false (no instance-specific parameters)
	 */
	@Override
	public boolean parse(String arg, String a, String b){
		return false;
	}
	
	/**
	 * Post-processing validation for parsed parameters.
	 * Ensures probability thresholds are converted from log10 format if negative
	 * and validates they fall within valid probability ranges [0,1].
	 */
	public static void postParseStatic(){
		minProb0=(minProb0>=0 ? minProb0 : (float)Math.pow(10, minProb0)); //Convert log10 to linear
		assert(minProb0<=1 && minProb0>=0) : minProb0;
		
		minProb1=(minProb1>=0 ? minProb1 : (float)Math.pow(10, minProb1)); //Convert log10 to linear
		assert(minProb1<=1 && minProb1>=0) : minProb1;
	}

	/*--------------------------------------------------------------*/
	/*----------------         Probability          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Initiates iterative refinement of probability matrices using default parameters.
	 * Delegates to parameterized refine method with maxHDist0, minRatio0, minProb0, and passes0.
	 * @param codeCounts Collection of observed barcodes with their frequencies
	 * @param minCount Minimum count threshold (currently must be <2, full implementation TODO)
	 */
	@Override
	public void refine(Collection<Barcode> codeCounts, long minCount){
		assert(minCount<2) : "TODO";
		refine(codeCounts, maxHDist0, minRatio0, minProb0, passes0);
	}
	
	/**
	 * Performs iterative expectation-maximization refinement of probability matrices.
	 * Each pass: (1) clears previous counts, (2) assigns barcodes to best matches using current probabilities,
	 * (3) accumulates base substitution counts, (4) recomputes probability matrix from counts.
	 * This iteratively improves probability estimates by alternating between assignment and parameter estimation.
	 * @param codeCounts Collection of observed barcodes with their frequencies
	 * @param maxHDist Maximum Hamming distance allowed for assignment
	 * @param minRatio Minimum ratio of best score to second-best score for assignment
	 * @param minProb Minimum probability threshold for assignment
	 * @param passes Number of refinement iterations to perform
	 */
	public void refine(Collection<Barcode> codeCounts, 
			int maxHDist, float minRatio, float minProb, int passes){
		if(passes<1){return;}
		Timer t=new Timer();
		ArrayList<Barcode> list=(codeCounts instanceof ArrayList ? 
				(ArrayList<Barcode>)codeCounts : new ArrayList<Barcode>(codeCounts));
		for(int i=0; i<passes; i++){
			clearCounts(); //Reset substitution count matrix
			t.start();
			populateCounts(list, maxHDist, minRatio, minProb); //E-step: assign reads to barcodes
			t.stop();
			makeProbs(); //M-step: update probability matrix from counts
			if(i==0){
//				System.err.println(toBytesProb(new ByteBuilder()));
//				System.err.println(toBytes(new ByteBuilder()));
//				int j=0;
//				for(Barcode bc : expectedList) {
//					j++;
//					if(j>200) {break;}
//					System.err.println(bc);
//				}
			}
			if(verbose){
				System.err.println(String.format("Refinement Pass %d Rate: \t%.4f\t%.4f", 
						(i+1), assignedFraction(), expectedFraction())+"\t"+t.timeInSeconds(2)+"s");
			}
		}
	}
	
	/**
	 * Creates the final barcode assignment map using default stringent parameters.
	 * Delegates to parameterized makeAssignmentMap with maxHDist1, minRatio1, and minProb1.
	 * Uses more stringent thresholds than refinement to ensure high-confidence assignments.
	 * @param codeCounts Collection of observed barcodes to assign
	 * @param minCount Minimum count threshold for processing a barcode
	 * @return HashMap mapping observed barcode sequences to expected barcode names
	 */
	@Override
	public HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCount){
		return makeAssignmentMap(codeCounts, minCount, maxHDist1, minRatio1, minProb1);
	}
	
	/**
	 * Creates final barcode assignment map using probabilistic scoring.
	 * Filters input barcodes by minCount threshold, then assigns each to closest expected barcode
	 * if it passes distance, ratio, and probability thresholds. Uses multi-threading for large datasets.
	 * Only assigns to expected barcodes (ref.expected==1), ignoring unexpected recombinant barcodes.
	 * @param codeCounts Collection of observed barcodes to assign
	 * @param minCount Minimum count threshold for processing a barcode
	 * @param maxHDist Maximum Hamming distance allowed for assignment
	 * @param minRatio Minimum ratio of best score to sum of competing scores
	 * @param minProb Minimum probability threshold for assignment
	 * @return HashMap mapping observed barcode sequences to expected barcode names
	 */
	public HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCount,
			int maxHDist, float minRatio, float minProb){
		Timer t=new Timer();
		assert(expectedList!=null && expectedList.size()>0) : expectedList;
		assert(codeCounts!=null);
		ArrayList<Barcode> list=highpass(codeCounts, minCount);
		HashMap<String, String> map=new HashMap<String, String>(Tools.max(200, list.size()/10));
		totalCounted=totalAssigned=totalAssignedToExpected=0;
		final long ops=list.size()*(long)expectedList.size();
		if(list.size()<2 || ops<100000 || Shared.threads()<2){ //Single-threaded for small datasets
			for(Barcode query : list){
				final String s=query.name;
				assert(s.length()==counts.length);
				Barcode ref=findClosest(s, maxHDist, minRatio, minProb);
				final long count=query.count();
				totalCounted+=count;
				if(ref!=null){
					totalAssigned+=count;
					if(ref.expected==1){ //Only assign to expected barcodes
						totalAssignedToExpected+=count;
						map.put(s, ref.name);
					}
				}
			}
		}else{
			populateCountsMT(list, maxHDist, minRatio, minProb, map); //Multi-threaded processing
		}
		t.stop();
		if(verbose){
			System.err.println(String.format("Final Assignment Rate:  \t%.4f\t%.4f", 
					assignedFraction(), expectedFraction())+"\t"+t.timeInSeconds(2)+"s");
		}
		return map;
	}

	/**
	 * Populates substitution count matrix for refinement using default parameters.
	 * Delegates to parameterized populateCounts with refinement thresholds (maxHDist0, etc.).
	 * @param list ArrayList of observed barcodes to process
	 * @param minCount Minimum count threshold (currently must be <2, full implementation TODO)
	 */
	@Override
	public void populateCounts(ArrayList<Barcode> list, long minCount){ //For refinement
		assert(minCount<2) : "TODO";
		populateCounts(list, maxHDist0, minRatio0, minProb0);
	}
	
	/**
	 * Populates substitution count matrix by assigning observed barcodes to expected references.
	 * Each successful assignment increments counts[position][observed_base][reference_base].
	 * Automatically chooses between single-threaded and multi-threaded processing based on workload size.
	 * @param list ArrayList of observed barcodes to process
	 * @param maxHDist Maximum Hamming distance allowed for assignment
	 * @param minRatio Minimum ratio of best score to sum of competing scores
	 * @param minProb Minimum probability threshold for assignment
	 */
	public void populateCounts(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb){
		assert(expectedList!=null && expectedList.size()>0) : expectedList;
		assert(list!=null);
		final long ops=list.size()*(long)expectedList.size();
		if(list.size()<2 || ops<100000 || Shared.threads()<2){
			populateCountsST(list, maxHDist, minRatio, minProb);
		}else{
			populateCountsMT(list, maxHDist, minRatio, minProb, null);
		}
	}

	/**
	 * Single-threaded implementation of count matrix population.
	 * Processes each observed barcode sequentially, finding best matching expected barcode
	 * and incrementing position-specific substitution counts.
	 * @param countList List of observed barcodes to process
	 * @param maxHDist Maximum Hamming distance for assignment
	 * @param minRatio Minimum score ratio for assignment
	 * @param minProb Minimum probability threshold for assignment
	 */
	private void populateCountsST(ArrayList<Barcode> countList,
			int maxHDist, float minRatio, float minProb){
		for(Barcode query : countList){
			final String s=query.name;
			assert(s.length()==counts.length);
			Barcode ref=findClosest(s, maxHDist, minRatio, minProb);
			add(s, ref, query.count()); //Accumulate substitution counts
		}
	}

	/**
	 * Multi-threaded implementation of count matrix population.
	 * Creates worker threads that process disjoint subsets of the input list in parallel.
	 * Each thread maintains local count matrices that are accumulated after completion.
	 * Thread count is limited to prevent overhead on small datasets.
	 * @param list List of observed barcodes to process
	 * @param maxHDist Maximum Hamming distance for assignment
	 * @param minRatio Minimum score ratio for assignment
	 * @param minProb Minimum probability threshold for assignment
	 * @param map Optional assignment map to populate (null during refinement)
	 */
	private void populateCountsMT(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb, 
			HashMap<String, String> map){
		//Do anything necessary prior to processing
		
		//Determine how many threads may be used
		final int threads=Tools.mid(1, Shared.threads(), list.size()/8);
		
		//Fill a list with PopThreads
		ArrayList<PopThread> alpt=new ArrayList<PopThread>(threads);
		for(int i=0; i<threads; i++){
			alpt.add(new PopThread(list, maxHDist, minRatio, minProb, map, i, threads));
		}
		
		//Start the threads and wait for them to finish
		boolean success=ThreadWaiter.startAndWait(alpt, this);
		errorState&=!success;
		
		//Do anything necessary after processing
		if(localCounts && map!=null){
			for(PopThread pt : alpt){
				synchronized(pt){map.putAll(pt.map);} //Merge thread-local assignment maps
			}
		}
	}
	
	/**
	 * Finds closest matching barcode using default stringent parameters.
	 * This method is not recommended for general use - use parameterized version instead.
	 * @param s Query barcode sequence
	 * @return Best matching expected barcode, or null if no acceptable match
	 */
	@Override
	public Barcode findClosest(String s){
		assert(false) : "Not recommended.";
		return findClosest(s, maxHDist1, minRatio1, minProb1);
	}
	
	/**
	 * Finds the closest matching expected barcode using probabilistic scoring with competitive assignment.
	 * Algorithm: (1) Computes probability-weighted scores for all expected barcodes,
	 * (2) Tracks best and second-best matches, (3) Applies distance, probability, and ratio filters.
	 * The ratio filter prevents assignment when multiple barcodes have similar scores (ambiguous matches).
	 * Score = P(query|reference) * frequency_weight, where P(query|reference) comes from position-specific
	 * substitution probability matrix and frequency_weight prevents over-assignment to rare barcodes.
	 * @param q Query barcode sequence to match
	 * @param maxHDist Maximum Hamming distance allowed for assignment
	 * @param minRatio Minimum ratio of (best_score / sum_of_all_scores) for unambiguous assignment
	 * @param minProb Minimum probability threshold (frequency-adjusted)
	 * @return Best matching expected barcode, or null if no acceptable match found
	 */
	public Barcode findClosest(String q, int maxHDist, float minRatio, float minProb){
		assert(!expectedList.isEmpty());
//		String best=expectedMap.get(q), best2=null;
		Barcode best=null, best2=null;
		
		//Note:  This ignores cases where you have a perfect match to the wrong barcode,
		//or two barcodes are within clearzone of each other.
		if(best!=null){return best;}
		double score=0, score2=0, sum=0;
		int hdist=hdist(length1, length2);
		int hdist2=hdist;
		assert(best==null);
		
		//Score all expected barcodes against query
		for(Barcode b : expectedList){
			final int d=hdist(q, b.name);
			double s=scoreUsingProbs(q, b.name)*b.frequency; //Probability * frequency weight
			if(s>score2 || (s==score2 && d<hdist2)){
				score2=s;
				best2=b;
				hdist2=d;
				if(s>score || (s==score && d<hdist)){ //New best match found
					sum+=score; //Add previous best to competitor sum
					score2=score;
					best2=best;
					hdist2=hdist;
					score=s;
					best=b;
					hdist=d;
				}else{
					sum+=s; //Add to competitor sum
				}
			}else{
				sum+=s; //Add to competitor sum
//				if(sum>minRatio) {return null;}
			}
		}

//		count--;
//		if(count>=0) {
//			System.err.println(q+", "+best+"\n"+
//					"h="+hdist+"\tsc="+score+"\tsum="+sum+"\tratio="+(float)(score/sum)+"\traw="+(score/best.frequency)
//					+"\nminProb="+(minProb*best.frequency)+"\tmaxSum="+(sum*minRatio));
//		}
//		assert(best!=null) : q+", maxHDist="+maxHDist+", clearzone="+clearzone+"\n"+
//		", hdMult="+hdMult+", minRatio="+minRatio+", minProb="+minProb+"\n"+
//		", score="+score+", score2="+score2+", sum="+sum+"\n"+
//		", hdist="+hdist+", hdist2="+hdist2+"\n";
		
		//Apply filtering thresholds
		if(best==null || hdist>maxHDist){return null;} //Distance filter
		assert(score<=1);
		assert(sum>=0);
		if(score<minProb/best.frequency){return null;} //Probability filter (frequency-adjusted)
		if(sum*minRatio>=score){return null;} //Ratio filter for ambiguous assignments
//		if(count>=0) {
//			System.err.println("pass");
//		}
		return best;
	}
//	static int count=10;
	
	/**
	 * Computes probabilistic alignment score between query and reference barcode.
	 * Delegates to static method using this instance's probability matrix.
	 * @param a Query barcode sequence
	 * @param b Reference barcode sequence
	 * @return Product of position-specific substitution probabilities
	 */
	public double scoreUsingProbs(final String a, final String b){
		return scoreUsingProbs(a, b, probs);
	}
	
	/**
	 * Computes probabilistic alignment score as product of position-specific substitution probabilities.
	 * For each position, multiplies P(observed_base|reference_base) from the probability matrix.
	 * Skips delimiter positions (non-ACGT characters) but ensures they match exactly.
	 * Result approaches 1.0 for perfect matches and decreases with more/higher-probability substitutions.
	 * @param a Query barcode sequence
	 * @param b Reference barcode sequence  
	 * @param probs 3D probability matrix [position][query_base][reference_base]
	 * @return Product of position-specific substitution probabilities
	 */
	public static double scoreUsingProbs(final String a, final String b, float[][][] probs){
//		if(verbose2) {
//			System.err.println("Comparing:\n"+a+"\n"+b);
//		}
		assert(a.length()==b.length()) : a+", "+b;
		final int min=Tools.min(a.length(), b.length());
//		int subs=0;
		double product=1;
		for(int i=0; i<min; i++){
			final char ca=a.charAt(i), cb=b.charAt(i);
			final int xa=baseToNumber[ca], xb=baseToNumber[cb];
			//assert(xb<4) : a+", "+b+", "+i;//This happens for the delimiter
			if(xb<4){ //Process ACGT bases only
//				subs+=(xa==xb ? 0 : 1);
				product*=probs[i][xa][xb]; //Multiply P(observed|reference) at position i
			}else{ //Delimiter or unknown character
				assert(ca==cb) : a+", "+b+", "+i; //Must match exactly
			}
//			if(verbose2) {
//				System.err.println(new ByteBuilder().append(ca).space().append(cb).space()
//						.append(probs[i][Tools.min(xa,3)][Tools.min(xb,3)], 5)
//						.append(" product=").append(""+product).append(" position=")
//						.append(i).space().append(xa).space().append(xb));
//			}
		}
		return product;
	}
	
	/**
	 * Updates probability matrix from accumulated substitution counts and adjusts barcode frequencies.
	 * Converts count matrix to probability matrix using maximum likelihood estimation with pseudocounts.
	 * Also recomputes frequency weights for expected barcodes, adding spoofReads to prevent
	 * over-assignment to very rare barcodes and normalizing by the most abundant barcode.
	 */
	public void makeProbs(){
//		assert(probs==null);
		fillProbs(counts, probs); //Convert counts to probabilities
		
//		System.err.println(toBytesProb(new ByteBuilder()));
		long sum=1, max=1;
		for(Barcode b : expectedList){
			sum+=b.count();
			max=Tools.max(max, b.count());
		}
		float mult=1.0f/(max+spoofReads); //Normalize by most abundant barcode + spoofing
		for(Barcode b : expectedList){
			b.frequency=Tools.mid(1.0f, (b.count()+spoofReads)*mult, minUnexpectedFrequency); //Bounded frequency
		}
	}

	/** Initializes probability matrix with default accuracy and frequency parameters. */
	public void initializeData(){initializeProbs(accuracy, 1.0f, 0.0001f);}
	/**
	 * Initializes probability matrix with uniform error model and sets barcode frequencies.
	 * Sets diagonal elements (correct calls) to correctCall probability and off-diagonal
	 * elements (substitution errors) to (1-correctCall)/3. This assumes uniform error rates
	 * across all positions and substitution types, which will be refined during iteration.
	 * @param correctCall Probability of correctly observing the true base
	 * @param goodBarcode Initial frequency weight for expected barcodes
	 * @param badBarcode Initial frequency weight for unexpected barcodes  
	 */
	public void initializeProbs(float correctCall, float goodBarcode, float badBarcode){
		final float incorrectCall=(1-correctCall)/3f; //Uniform error rate across 3 possible substitutions
		for(int pos=0; pos<probs.length; pos++){
			for(int xc=0; xc<5; xc++){
				for(int xr=0; xr<4; xr++){
					probs[pos][xc][xr]=incorrectCall; //Initialize with error probability
				}
				if(xc<4){probs[pos][xc][xc]=correctCall;} //Diagonal = correct call probability
			}
		}
		for(Barcode b : expectedList){
			b.frequency=(b.expected==1 ? goodBarcode : badBarcode);
		}
	}
	
	/**
	 * Converts substitution count matrix to probability matrix using maximum likelihood estimation.
	 * For each position and query base, computes P(reference_base|query_base) by normalizing
	 * counts across all reference bases. Adds pseudocounts (+1) to prevent zero probabilities
	 * which would cause numerical issues during scoring.
	 * @param counts Input count matrix [position][query_base][reference_base]
	 * @param probs Output probability matrix [position][query_base][reference_base]
	 */
	public static void fillProbs(long[][][] counts, float[][][] probs){
		for(int pos=0; pos<counts.length; pos++){
			for(int xq=0; xq<5; xq++){
//				final byte q=numberToBase[xq];
				final long[] crow=counts[pos][xq];
				final float[] prow=probs[pos][xq];
				final long sum=crow[0]+crow[1]+crow[2]+crow[3]+1; //Sum counts, ignore noref, add pseudocount
				final float mult=1.0f/(Tools.max(sum, 1));
				for(int xr=0; xr<prow.length; xr++){
					prow[xr]=(crow[xr]+1)*mult; //MLE with +1 pseudocount
				}
			}
		}
//		return probs;
	}

	/*--------------------------------------------------------------*/
	/*----------------          Populating          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Populates unexpected barcode combinations using default frequency calculation.
	 * Delegates to parameterized version with frequency=-1 to trigger automatic calculation.
	 */
	@Override
	public void populateUnexpected(){populateUnexpected(-1);}
	
	/**
	 * Populates unexpected barcode combinations for dual-barcode systems.
	 * Generates all possible combinations of left and right barcode segments from expected barcodes.
	 * This handles PCR recombination events where legitimate barcode segments recombine
	 * into unexpected but valid combinations. Frequency is calculated based on
	 * baseRecombinationRate or set explicitly if provided.
	 * @param frequency Frequency weight for unexpected combinations (if <=0, calculated automatically)
	 */
	public void populateUnexpected(float frequency){
		assert(length1>0 && length2>0) : "This is only for dual barcodes.";
		LinkedHashSet<String> set=new LinkedHashSet<String>();
		LinkedHashSet<String> set1=new LinkedHashSet<String>();
		LinkedHashSet<String> set2=new LinkedHashSet<String>();
//		ArrayList<String> leftList=new ArrayList<String>(expectedList.size());
//		ArrayList<String> rightList=new ArrayList<String>(expectedList.size());
		for(Barcode b : expectedList){
			assert(b.expected==1);
			assert(!set.contains(b.name)) :"Duplicate expected barcode: "+b.name+"; set.size="+set.size();
			set.add(b.name);
			String code1=b.name.substring(0, length1); //Left segment
			String code2=b.name.substring(start2); //Right segment
			set1.add(code1);
			set2.add(code2);
		}
		long combos=set1.size()*set2.size();
		
//		System.err.println(frequency);
		if(frequency<=0){ //Calculate frequency automatically
			frequency=(baseRecombinationRate*set.size())/combos;
//			System.err.println(frequency);
			frequency=Tools.mid(frequency, minUnexpectedFrequency0, 1f);
//			System.err.println(frequency+", "+minUnexpectedFrequency0);
		}
		
		ByteBuilder bb=new ByteBuilder(length);
		for(String code1 : set1){
			for(String code2 : set2){
				bb.clear();
				bb.append(code1);
				if(delimiter>0){bb.append((byte)delimiter);}
				bb.append(code2);
				String code=bb.toString();
				if(!set.contains(code)){ //Only add new combinations
					set.add(code);
					Barcode b=new Barcode(code, 0, 0);
					b.frequency=frequency;
					expectedList.add(b);
					//I could add it to the map here too, but it's not necessary
				}
			}
		}
	}
	
	/**
	 * Calculates the best alternative score for a query without applying filtering thresholds.
	 * Similar to findClosest but returns the raw score rather than the barcode object.
	 * Used for assignment quality assessment and debugging.
	 * @param q Query barcode sequence
	 * @return Best probability-weighted score found
	 */
	public double calcAltScore(String q){
		assert(!expectedList.isEmpty());
//		String best=expectedMap.get(q), best2=null;
		Barcode best=null, best2=null;
		
		//Note:  This ignores cases where you have a perfect match to the wrong barcode,
		//or two barcodes are within clearzone of each other.
		if(best!=null){return 0;}
		double score=0, score2=0, sum=0;
//		int hdist=hdist(length1, length2);
//		int hdist2=hdist;
		assert(best==null);
		
		for(Barcode b : expectedList){
			final int d=hdist(q, b.name);
			double s=scoreUsingProbs(q, b.name)*b.frequency;
			if(s>score2){
				score2=s;
				best2=b;
//				hdist2=d;
				if(s>score){
					sum+=score;
					score2=score;
					best2=best;
//					hdist2=hdist;
					score=s;
					best=b;
//					hdist=d;
				}else{
					sum+=s;
				}
			}else{
				sum+=s;
			}
		}
		return score;
	}
	
	/**
	 * Formats probability matrix as tab-delimited text for debugging and visualization.
	 * Output format: position, query_base, P(A|query), P(C|query), P(G|query), P(T|query)
	 * @param bb ByteBuilder to append to (created if null)
	 * @return ByteBuilder containing formatted probability matrix
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
					bb.tab().append(prob,4);
				}
				bb.nl();
			}
		}
		return bb;
	}
	
	/**
	 * Writes assignment map to file with detailed scoring information.
	 * Output columns: query_barcode, assigned_barcode, hamming_distance, 
	 * probability_score, alternative_score, [read_count]
	 * @param assignmentMap Map of query barcodes to assigned reference barcodes
	 * @param mapOut Output file path
	 * @param counts Optional map of query barcode counts for additional column
	 * @param overwrite Whether to overwrite existing output file
	 * @param append Whether to append to existing output file
	 */
	@Override
	public void printAssignmentMap(HashMap<String, String> assignmentMap,
			String mapOut, HashMap<String, Barcode> counts, boolean overwrite, boolean append){

		ByteStreamWriter bsw=new ByteStreamWriter(mapOut, overwrite, append, true);
		bsw.start();
		for(Entry<String, String> e : assignmentMap.entrySet()){
			String a=e.getKey(), b=e.getValue();
			bsw.print(a).tab().print(b);
			bsw.tab().print(Barcode.hdist(a, b));
			Barcode bc=getBarcode(b);
			double pscore=scoreUsingProbs(a, bc.name)*bc.frequency;
			bsw.tab().print(pscore, 5);
			bsw.tab().print(calcAltScore(a), 5);
			Barcode v=(counts==null ? null : counts.get(a));
			if(v!=null){bsw.tab().print(v.count());}
			bsw.nl();
		}
		bsw.poisonAndWait();
	}
	
	/** Validates licensing requirements for this closed-source component. */
	protected boolean valid(){return fun.Dongle.check(this);}
	
	/*--------------------------------------------------------------*/

	/**
	 * Worker thread for multi-threaded barcode assignment and count population.
	 * Each thread processes a disjoint subset of the input barcode list using
	 * stride-based partitioning (thread i processes elements i, i+threads, i+2*threads, ...).
	 * Maintains thread-local counts and assignment maps when localCounts is enabled.
	 */
	class PopThread extends Thread{

		/**
		 * Creates a worker thread for processing a subset of barcode assignments.
		 * @param list_ Complete list of barcodes to process
		 * @param maxHDist_ Maximum Hamming distance for assignment
		 * @param minRatio_ Minimum score ratio for assignment
		 * @param minProb_ Minimum probability threshold for assignment
		 * @param map_ Assignment map to populate (null during refinement)
		 * @param tid_ Thread ID (0-based index)
		 * @param threads_ Total number of threads
		 */
		public PopThread(ArrayList<Barcode> list_,
				int maxHDist_, float minRatio_, float minProb_,
				HashMap<String, String> map_, int tid_, int threads_){
			list=list_;
			maxHDist=maxHDist_;
			minRatio=minRatio_;
			minProb=minProb_;
			tid=tid_;
			threads=threads_;
			map=(map_==null ? null : localCounts ? new HashMap<String, String>() : map_);
			countsT=(localCounts ? new long[length][5][5] : null); //Thread-local count matrix
		}

		/**
		 * Processes assigned subset of barcode list using stride-based partitioning.
		 * For each query barcode, finds closest expected barcode and updates count matrices.
		 * Uses thread-local storage when localCounts is enabled to avoid synchronization overhead.
		 */
		@Override
		public void run(){
			for(int i=tid; i<list.size(); i+=threads){ //Stride-based work distribution
				Barcode query=list.get(i);
				final String s=query.name;
				assert(s.length()==counts.length);
				Barcode ref=findClosest(s, maxHDist, minRatio, minProb);
				if(localCounts){
					addT(s, ref, query.count()); //Thread-local accumulation
					if(map!=null && ref!=null && ref.expected==1){map.put(s, ref.name);}
				}else{
					synchronized(counts){ //Synchronized access to shared matrix
						add(s, ref, query.count());
						if(map!=null && ref!=null && ref.expected==1){map.put(s, ref.name);}
					}
				}
			}
		}
		
		/**
		 * Thread-local version of substitution count accumulation.
		 * Increments position-specific counts in thread-local matrix to avoid synchronization.
		 * Also updates assignment statistics and reference barcode counts.
		 * @param query Query barcode sequence
		 * @param ref Reference barcode (null if no assignment)
		 * @param count Number of reads with this query sequence
		 */
		public void addT(String query, Barcode ref, long count){
			assert(ref==null || ref.length()==countsT.length);
			for(int i=0; i<query.length(); i++){
				final int q=query.charAt(i), r=(ref==null ? 'N' : ref.charAt(i));
				final byte xq=baseToNumber[q], xr=baseToNumber[r];
				countsT[i][xq][xr]+=count; //Thread-local count increment
			}
			totalCountedT+=count;
			if(ref!=null){
				ref.incrementSync(count); //Synchronized increment of reference barcode
				totalAssignedT+=count;
				totalAssignedToExpectedT+=ref.expected*count;
			}
		}

		/** List of barcodes assigned to this thread */
		final ArrayList<Barcode> list;
		/** Maximum Hamming distance for assignment */
		final int maxHDist;
		/** Minimum score ratio for unambiguous assignment */
		final float minRatio;
		/** Minimum probability threshold for assignment */
		final float minProb;
		/** Thread ID for stride-based partitioning */
		final int tid;
		/** Total number of threads */
		final int threads;
		/** Thread-local assignment map (when localCounts enabled) */
		final HashMap<String, String> map;
		
		/** Thread-local substitution count matrix [position][query][ref] */
		final long[][][] countsT;
		/** Thread-local count of processed reads */
		long totalCountedT;
		/** Thread-local count of successfully assigned reads */
		long totalAssignedT;
		/** Thread-local count of reads assigned to expected barcodes */
		long totalAssignedToExpectedT;
	}

	/**
	 * Accumulates thread-local statistics into main matrices after thread completion.
	 * Only performs accumulation when localCounts is enabled (thread-local storage mode).
	 * @param t PopThread whose local counts should be merged into global totals
	 */
	@Override
	public void accumulate(PopThread t){
		if(localCounts){
			synchronized(t){
				Tools.add(counts, t.countsT); //Add thread-local counts to main matrix
				totalCounted+=t.totalCountedT;
				totalAssigned+=t.totalAssignedT;
				totalAssignedToExpected+=t.totalAssignedToExpectedT;
			}
		}
	}

	/**
	 * Returns success status for ThreadWaiter interface.
	 * Currently always returns false - success determination is handled elsewhere.
	 * @return Always returns false (TODO: implement proper success logic)
	 */
	@Override
	public boolean success(){
		// TODO Auto-generated method stub
		return false;
	}
	
	/*--------------------------------------------------------------*/
	
	/** 3D probability matrix storing P(observed_base|reference_base) at each position */
	final float[][][] probs;
	
	/** Unused frequency matrix for barcode combinations */
	private float[][] comboFrequency;
	
	/*--------------------------------------------------------------*/
	
	/** Maximum Hamming distance for refinement phase (more permissive) */
	static int maxHDist0=12;
	/** Minimum score ratio for refinement phase (more permissive) */
	static float minRatio0=20f;
	/** Minimum probability threshold for refinement phase (log10 format) */
	static float minProb0=-16;
	/** Number of refinement iterations to perform */
	static int passes0=5;

	/** Maximum Hamming distance for final assignment phase (more stringent) */
	static int maxHDist1=10;
	/** Minimum score ratio for final assignment phase (very stringent) */
	static float minRatio1=1_000_000f;
	/** Minimum probability threshold for final assignment phase (log10 format) */
	static float minProb1=-5.5f;
	
	/*--------------------------------------------------------------*/
	
	/** Pseudocount reads added to prevent over-assignment to very rare barcodes */
	static int spoofReads=4; //4 or even 16 seemed good for a full lane 89-way but maybe not for small datasets
	
	/** Base recombination rate for generating unexpected barcode combinations */
	private static float baseRecombinationRate=0.08f; //This is a bit high; measured at 0.033. However, since it is nonuniform, a high value is safer.
	/** Minimum frequency for unexpected barcodes during refinement */
	private static float minUnexpectedFrequency0=0.00002f; //Normally overridden by baseRecombinationRate
	/** Minimum frequency for unexpected barcodes during final assignment */
	private static float minUnexpectedFrequency=0.0000001f; //Normally overridden by spoofReads
	/** Default sequencing accuracy for probability matrix initialization */
	protected static float accuracy=0.82f;
	
	
}
