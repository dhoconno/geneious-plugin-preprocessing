package barcode.prob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import barcode.Barcode;
import barcode.PCRMatrix;
import shared.Parse;
import shared.Shared;
import shared.Timer;
import shared.Tools;
import structures.ByteBuilder;

/**
 * Abstract superclass for probabilistic PCR barcode assignment using maximum likelihood estimation.
 * Implements expectation-maximization algorithms to learn base transition probabilities from observed
 * data and assigns barcodes using position-specific error models. The core data structure is a 3D 
 * probability matrix [position][called_base][reference_base] that captures sequencing error patterns
 * at each barcode position. Uses iterative refinement to converge on optimal probability estimates
 * and applies strict thresholds during final assignment to ensure high-confidence barcode calls.
 * 
 * This class is closed-source.
 * It is not legal to redistribute, replicate, or reverse-engineer.
 * 
 * @author Brian Bushnell
 * @date May 15, 2024
 *
 */
public abstract class PCRMatrixProbAbstract extends PCRMatrix {

	/*--------------------------------------------------------------*/
	/*----------------         Constructor          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Constructs a new PCRMatrixProbAbstract with probabilistic barcode matching capabilities.
	 * Initializes the 3D probability matrix [position][call][reference] where calls are A,C,G,T,N
	 * and references are A,C,G,T. The probability matrix tracks the likelihood of observing
	 * each reference base given each called base at each position in the barcode sequence.
	 * @param length1_ Length of first barcode segment
	 * @param length2_ Length of second barcode segment  
	 * @param delimiter_ ASCII value of delimiter character between segments
	 * @param hdistSum_ Whether to sum hamming distances across segments
	 * @param byTile_ Whether to calculate tile-specific probability matrices
	 */
	public PCRMatrixProbAbstract(int length1_, int length2_, int delimiter_, boolean hdistSum_, boolean byTile_){
		super(length1_, length2_, delimiter_, hdistSum_);
		writelock();
		probs=new float[length][5][4]; //[position][call A,C,G,T,N][ref A,C,G,T]
		byTile=byTile_;
		assert(byTile_==byTile());
		writeunlock();
	}

	/*--------------------------------------------------------------*/
	/*----------------           Parsing            ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Parses static configuration parameters for probabilistic barcode assignment.
	 * Handles parameters controlling base recombination rates, frequency thresholds,
	 * accuracy settings, refinement passes, and scoring criteria. Parameters can be
	 * specified in scientific notation (negative values converted via Math.pow(10,x)).
	 * @param arg The full argument string (unused but maintained for interface compatibility)
	 * @param a The parameter name (case-insensitive for most parameters)
	 * @param b The parameter value as string
	 * @return true if parameter was recognized and parsed, false otherwise
	 */
	public static final boolean parseStatic(String arg, String a, String b){
		
		if(a.equals("brrate") || a.equals("baserecombinationrate")){
			baseRecombinationRate=Float.parseFloat(b);
		}else if(a.equals("unexpectedmult")){
			unexpectedMult=Float.parseFloat(b);
		}else if(a.equalsIgnoreCase("minUnexpectedFrequency0") || a.equals("minunfreq0")){
			minUnexpectedFrequency0=Float.parseFloat(b);
			if(minUnexpectedFrequency0<0){minUnexpectedFrequency0=(float)Math.pow(10, minUnexpectedFrequency0);} //Convert from log scale
		}else if(a.equalsIgnoreCase("minUnexpectedFrequency") || a.equals("minunfreq")){
			minUnexpectedFrequency=Float.parseFloat(b);
			if(minUnexpectedFrequency<0){minUnexpectedFrequency=(float)Math.pow(10, minUnexpectedFrequency);} //Convert from log scale
		}else if(a.equalsIgnoreCase("spoofReads") || a.equals("spoof")){
			spoofReads=Integer.parseInt(b); //Pseudocount for low-frequency barcodes
		}
		
		else if(a.equals("accuracy")){
			accuracy=Float.parseFloat(b);
			assert(accuracy>0 && accuracy<1);
		}else if(a.equals("avgweight") || a.equals("tileweight")){
			TILE_WEIGHT=Float.parseFloat(b);
			assert(TILE_WEIGHT>=0);
		}else if(a.equals("fbranch")){
			FBRANCH=Integer.parseInt(b);
		}else if(a.equals("hybrid") || a.equalsIgnoreCase("hybridhdist")){
			hybridHDist=Tools.startsWithDigit(b) ? Integer.parseInt(b) : Parse.parseBoolean(b) ? 1 : -1; //Parse as int or boolean
		}else if(a.equalsIgnoreCase("hybridClearzone") || a.equals("hybridcz")){
			hybridClearzone=Integer.parseInt(b);
		}else if(a.equalsIgnoreCase("splitList")){
			splitList=Parse.parseBoolean(b);
		}
		
		else if(a.equalsIgnoreCase("minExpectedFrequency") || a.equals("minexfreq")){
			minExpectedFrequency=Float.parseFloat(b);
			if(minExpectedFrequency<0){minExpectedFrequency=(float)Math.pow(10, minExpectedFrequency);} //Convert from log scale
		}else if(a.equalsIgnoreCase("minExpectedScore") || a.equals("minexscore")){
			minExpectedScore=Float.parseFloat(b);
			if(minExpectedScore<0){minExpectedScore=(float)Math.pow(10, minExpectedScore);} //Convert from log scale
		}else if(a.equalsIgnoreCase("expectedRatio1") || a.equals("eratio1") || a.equals("eratio")){
			expectedRatio1=(float)Parse.parseDoubleKMG(b);
		}else if(a.equals("minbaseprob") || a.equals("mintransitionprob") || a.equals("minbprob")){
			minBaseProb=(float)Parse.parseDoubleKMG(b);
			if(minBaseProb<0){minBaseProb=(float)Math.pow(10, minBaseProb);} //Convert from log scale
		}else if(a.equals("mincorrectbaseprob") || a.equals("mincbprob")){
			minCorrectBaseProb=(float)Parse.parseDoubleKMG(b);
			if(minCorrectBaseProb<0){minCorrectBaseProb=(float)Math.pow(10, minCorrectBaseProb);} //Convert from log scale
		}else if(a.equalsIgnoreCase("sqrtFrequency") || a.equals("sqrtf")){
			sqrtFrequency=Parse.parseBoolean(b);
		}
		
		else if(a.equals("passes") || a.equals("refinepasses") || a.equals("pcrpasses")){
			passes0=Integer.parseInt(b); //Number of refinement iterations
		}else if(a.equals("maxhdist0") || a.equals("hdist0")){
			maxHDist0=Integer.parseInt(b); //Max hamming distance for refinement phase
		}else if(a.equals("maxhdist") || a.equals("hdist") || a.equals("maxhdist1") || a.equals("hdist1")){
			maxHDist1=Integer.parseInt(b); //Max hamming distance for assignment phase
		}else if(a.equals("minratio0") || a.equals("ratio0")){
			minRatio0=(float)Parse.parseDoubleKMG(b);
			assert(minRatio0>=0) : minRatio0;
		}else if(a.equals("minratio1") || a.equals("ratio1") || a.equals("ratio") || a.equals("minratio")){
			minRatio1=(float)Parse.parseDoubleKMG(b);
			assert(minRatio1>=0) : minRatio1;
		}else if(a.equals("minprob0")){
			minProb0=(float)Parse.parseDoubleKMG(b);
			minProb0=(minProb0>=0 ? minProb0 : (float)Math.pow(10, minProb0)); //Convert from log if negative
			assert(minProb0<=1 && minProb0>=0) : minProb0;
		}else if(a.equals("minprob1") || a.equals("minprob")){
			minProb1=(float)Parse.parseDoubleKMG(b);
			minProb1=(minProb1>=0 ? minProb1 : (float)Math.pow(10, minProb1)); //Convert from log if negative
			assert(minProb1<=1 && minProb1>=0) : minProb1;
		}else{
			return false;
		}
		return true;
	}

	/**
	 * Instance-level parameter parsing method. Currently unused as all parameters
	 * are handled at the static level via parseStatic().
	 * @param arg The full argument string
	 * @param a The parameter name
	 * @param b The parameter value
	 * @return Always returns false as no instance parameters are supported
	 */
	@Override
	public final boolean parse(String arg, String a, String b){
		return false;
	}
	
	/**
	 * Performs post-processing validation on parsed probability parameters.
	 * Ensures minProb0 and minProb1 are converted from log scale if necessary
	 * and validates they fall within valid probability range [0,1].
	 * Called after all static parameter parsing is complete.
	 */
	public final static void postParseStatic(){
		minProb0=(minProb0>=0 ? minProb0 : (float)Math.pow(10, minProb0)); //Convert from log if negative
		assert(minProb0<=1 && minProb0>=0) : minProb0;
		
		minProb1=(minProb1>=0 ? minProb1 : (float)Math.pow(10, minProb1)); //Convert from log if negative
		assert(minProb1<=1 && minProb1>=0) : minProb1;
	}

	/*--------------------------------------------------------------*/
	/*----------------         Probability          ----------------*/
	/*--------------------------------------------------------------*/
	
	
	/**
	 * Initiates barcode refinement using default parameters for the first phase.
	 * Delegates to the full refine() method with preconfigured thresholds optimized
	 * for initial probability matrix estimation.
	 * @param codeCounts Collection of barcode observations with their counts
	 * @param minCountR Minimum read count threshold for barcode consideration
	 */
	@Override
	public final void refine(Collection<Barcode> codeCounts, long minCountR){
		refine(codeCounts, maxHDist0, minRatio0, minProb0, passes0, minCountR);
	}
	
	/**
	 * Performs iterative refinement of the probability matrix using expectation-maximization algorithm.
	 * Implements a two-phase EM process: E-step assigns barcodes to best matches using current probabilities,
	 * M-step updates probability matrix based on assignment counts. Converges when probability changes
	 * fall below numerical precision thresholds. The algorithm maintains thread-safe exclusive write
	 * access during probability updates while allowing concurrent read access during count population.
	 * Uses reader-writer locks to maximize parallelization during the computationally expensive
	 * population phase. Typically converges within 4-5 iterations for most sequencing datasets.
	 * @param codeCounts Collection of observed barcodes with read counts for EM training
	 * @param maxHDist Maximum hamming distance allowed for barcode matching (controls search space)
	 * @param minRatio Minimum ratio between best and second-best barcode scores (prevents ambiguous assignments)
	 * @param minProb Minimum probability threshold for barcode assignment (quality filter)
	 * @param passes Number of EM iterations to perform (4-5 typically sufficient for convergence)
	 * @param minCountR Minimum read count for barcode to be considered during refinement
	 */
	public final void refine(Collection<Barcode> codeCounts, 
			int maxHDist, float minRatio, float minProb, int passes, long minCountR){
		if(passes<1){return;} //No refinement requested
		writelock(); //Exclusive access for EM initialization
		if(verbose){
			if(devMode){
				System.err.println("Pair Assignment Rate:   \tTotal\tGood\tBad");
			}else{
				System.err.println("Assigning Barcodes");
			}
		}
		Timer t=new Timer(), t2=new Timer();
		ArrayList<Barcode> list=(codeCounts instanceof ArrayList ? 
				(ArrayList<Barcode>)codeCounts : new ArrayList<Barcode>(codeCounts)); //Ensure ArrayList for indexed access
		for(int i=0; i<passes; i++){ //EM iteration loop
			clearCounts(); //Reset transition counts for M-step
			t.start();
			writeunlock(); //Allow concurrent access during E-step
			populateCounts(list, maxHDist, minRatio, minProb, minCountR); //E-step: assign reads to barcodes
			writelock(); //Re-acquire lock for M-step probability update
			t.stop();
			makeProbs(); //M-step: update probability matrix from counts
			if(verbose && devMode){
				System.err.println(String.format("Refinement Pass %d Rate: \t%.4f\t%.4f\t%.6f", 
					(i+1), assignedFraction(), expectedFraction(), chimericFraction())+
						"\t"+t.timeInSeconds(2)+"s");
			}
		}
		writeunlock();
		if(verbose){
			if(devMode){t2.stop("Refinement Time:\t\t");}
			else{t2.stop("Calculation Time:\t\t");}
		}
	}
	
	/**
	 * Creates final barcode assignment mapping using default parameters for assignment phase.
	 * Uses stricter thresholds than refinement phase to ensure high-confidence assignments.
	 * @param codeCounts Collection of barcode observations with their counts
	 * @param minCountA Minimum count threshold for assignment consideration
	 * @return HashMap mapping observed sequences to assigned barcode names
	 */
	@Override
	public final HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCountA){
		return makeAssignmentMap(codeCounts, minCountA, maxHDist1, minRatio1, minProb1);
	}
	
	/**
	 * Creates barcode assignment mapping with specified quality thresholds.
	 * Abstract method implemented by concrete subclasses to handle specific
	 * assignment strategies (e.g., single-ended vs paired-end).
	 * @param codeCounts Collection of barcode observations with their counts
	 * @param minCountA Minimum count threshold for assignment consideration
	 * @param maxHDist Maximum hamming distance allowed for barcode matching
	 * @param minRatio Minimum ratio between best and second-best barcode scores
	 * @param minProb Minimum probability threshold for barcode assignment
	 * @return HashMap mapping observed sequences to assigned barcode names
	 */
	public abstract HashMap<String, String> makeAssignmentMap(Collection<Barcode> codeCounts, long minCountA,
			int maxHDist, float minRatio, float minProb);

	/**
	 * Populates transition counts using default refinement parameters.
	 * Wrapper method that delegates to the full populateCounts method.
	 * @param list ArrayList of barcode observations for counting
	 * @param minCount Minimum count threshold for barcode consideration
	 */
	@Override
	public final void populateCounts(ArrayList<Barcode> list, long minCount){ //For refinement
		populateCounts(list, maxHDist0, minRatio0, minProb0, minCount);
	}
	
	/**
	 * Populates base transition counts by comparing observed barcodes against expected sequences.
	 * Automatically chooses between single-threaded and multi-threaded execution based on
	 * problem size and available processors. Uses dynamic programming approach to minimize
	 * redundant computations across multiple barcode comparisons.
	 * @param list ArrayList of observed barcode sequences with counts
	 * @param maxHDist Maximum hamming distance for barcode matching
	 * @param minRatio Minimum ratio between best and second-best scores
	 * @param minProb Minimum probability threshold for assignment
	 * @param minCount Minimum count threshold for barcode consideration
	 */
	public final void populateCounts(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb, long minCount){
		assert(expectedList!=null && expectedList.size()>0) : expectedList;
		assert(list!=null);
		final long ops=list.size()*(long)expectedList.size(); //Total comparison operations
		if(list.size()<2 || ops<100000 || Shared.threads()<2){ //Use single-threaded for small problems
			populateCountsST(list, maxHDist, minRatio, minProb, minCount);
		}else{ //Multi-threaded for large datasets
			populateCountsMT(list, maxHDist, minRatio, minProb, minCount, null);
		}
	}

	/**
	 * Single-threaded implementation of count population.
	 * Abstract method implemented by concrete subclasses.
	 * @param countList List of barcode observations to process
	 * @param maxHDist Maximum hamming distance for matching
	 * @param minRatio Minimum ratio between top scores
	 * @param minProb Minimum probability threshold
	 * @param minCount Minimum count threshold
	 */
	abstract void populateCountsST(ArrayList<Barcode> countList,
			int maxHDist, float minRatio, float minProb, long minCount);

	/**
	 * Multi-threaded implementation of count population.
	 * Abstract method implemented by concrete subclasses to handle parallel processing.
	 * @param list List of barcode observations to process
	 * @param maxHDist Maximum hamming distance for matching
	 * @param minRatio Minimum ratio between top scores  
	 * @param minProb Minimum probability threshold
	 * @param minCount Minimum count threshold
	 * @param map Optional assignment map for concurrent building
	 */
	abstract void populateCountsMT(ArrayList<Barcode> list,
			int maxHDist, float minRatio, float minProb, long minCount,
			HashMap<String, String> map);
	
	/**
	 * Calculates probability-based similarity score between two barcode sequences.
	 * Uses the current probability matrix to score alignment quality.
	 * @param a First barcode sequence (observed)
	 * @param b Second barcode sequence (expected)
	 * @param pos Starting position in probability matrix
	 * @return Multiplicative probability score (product of position-specific probabilities)
	 */
	public final double scoreUsingProbs(final byte[] a, final byte[] b, int pos){
		return scoreUsingProbs(a, b, pos, probs);
	}
	
	/**
	 * Static method to calculate probability-based alignment score using empirical error model.
	 * Implements position-specific scoring where each base comparison contributes its learned
	 * probability from the 3D matrix. Uses multiplicative scoring: perfect matches approach 1.0,
	 * single mismatches typically score 0.01-0.1, multiple mismatches decay exponentially.
	 * This scoring scheme strongly favors high-confidence assignments while maintaining
	 * sensitivity to systematic sequencing errors. Handles nucleotide positions using probability
	 * lookups and delimiter characters by requiring exact matches. The algorithm converts ASCII
	 * bases to 0-4 indices (A,C,G,T,N) for matrix access and accumulates log-likelihood ratios.
	 * @param a First sequence (typically observed barcode from sequencing read)
	 * @param b Second sequence (typically expected barcode from reference library) 
	 * @param pos Starting position in the probability matrix (accounts for barcode segment offset)
	 * @param probs 3D empirical probability matrix [position][called_base][reference_base]
	 * @return Product of position-specific match probabilities (range: 0-1, higher = better match)
	 */
	public static final double scoreUsingProbs(final byte[] a, final byte[] b, int pos, float[][][] probs){
		if(verbose2){
			System.err.println("Comparing2:\n"+new String(a)+"\n"+new String(b));
		}
		assert(a.length==b.length || (Tools.endsWithLetter(a)!=Tools.endsWithLetter(b)
				&& Tools.startsWithLetter(a) && Tools.startsWithLetter(b))) : 
			new String(a)+", "+new String(b);
		final int min=Tools.min(a.length, b.length);
//		int subs=0;
		double product=1f; //Multiplicative probability accumulator
		for(int i=0; i<min; i++, pos++){ //Iterate through aligned positions
			final byte ca=a[i], cb=b[i];
			final int xa=baseToNumber[ca], xb=baseToNumber[cb]; //Convert ASCII to 0-4 indices (A,C,G,T,N)
			//assert(xb<4) : a+", "+b+", "+i;//This happens for the delimiter
			if(xb<4){ //Valid nucleotide position (not delimiter)
//				subs+=(xa==xb ? 0 : 1);
				product*=probs[pos][xa][xb]; //Multiply by empirical transition probability P(observed|expected)
			}else{ //Delimiter character - must match exactly
				assert(ca==cb) : a+", "+b+", "+i; //Delimiters cannot be substituted
			}
			if(verbose2){
				System.err.println(new ByteBuilder().append(ca).space().append(cb).space()
						.append(probs[pos][Tools.min(xa,3)][Tools.min(xb,3)], 5)
						.append(" product=").append(""+product).append(" position=")
						.append(pos).space().append(xa).space().append(xb));
			}
		}
		return product;
	}
	
	/**
	 * Constructs probability matrices from accumulated base transition counts using maximum likelihood
	 * estimation with Laplace smoothing. Implements the M-step of the EM algorithm by converting
	 * raw transition counts to normalized probabilities and updating barcode frequency estimates.
	 * Uses pseudocounts (+1 per transition) to prevent zero probabilities that would cause
	 * numerical instability. Calculates barcode frequencies using empirical read counts normalized
	 * by the maximum count plus spoofReads pseudocount. Applies optional square-root frequency
	 * transformation to reduce dynamic range and improve sensitivity to low-frequency expected barcodes.
	 * Multiplies unexpected barcode frequencies by penalty factor to discourage spurious assignments.
	 */
	@Override
	public final void makeProbs(){
		writelock();
//		assert(probs==null);
		fillProbs(counts, probs); //Convert counts to probabilities with pseudocounts
		if(byTile){makeTileProbs();} //Optional tile-specific probability matrices
		
//		System.err.println(toBytesProb(new ByteBuilder()));
		long sum=1, max=1; //Track total and maximum barcode counts
		for(Barcode b : expectedList){ //Find maximum count for normalization
			sum+=b.count();
			max=Tools.max(max, b.count());
		}
		float mult=1.0f/(max+spoofReads); //Normalization factor including pseudocounts
		for(int i=0; i<allCodes.length; i++){
			Barcode b=allCodes[i];
			b.frequency=Tools.mid(1.0f, (b.count()+spoofReads)*mult, minUnexpectedFrequency); //Normalize with lower bound
			if(b.expected==0){b.frequency*=unexpectedMult;} //Penalize unexpected barcodes to reduce false assignments
			else if(sqrtFrequency){ //Apply square-root transformation for expected barcodes
				//If desired, this is where minExpectedFrequency should be applied.
//				float f=b.frequency;
				b.frequency=Tools.max(b.frequency, (float)Math.sqrt(b.frequency*minExpectedFrequency)); //Boost low-frequency expected barcodes
//				assert(b.frequency>minExpectedFrequency) : 
//					"\n"+f+"->"+b.frequency+", "+minExpectedFrequency+", "+b.name+", "+b.count()+"\n";
			}
			comboFrequency[i]=b.frequency; //Cache frequencies for fast lookup during scoring
		}
		writeunlock();
	}
	
	/**
	 * Creates tile-specific probability matrices for flow cell position effects.
	 * Abstract method that should be overridden by subclasses supporting tile-aware analysis.
	 * Throws RuntimeException if called on base class.
	 */
	void makeTileProbs(){throw new RuntimeException("Invalid method for class.");}

	/**
	 * Initializes probability matrices and frequency arrays with default parameters.
	 * Sets up initial state before any barcode observations are processed.
	 */
	public final void initializeData(){
		initializeProbs(accuracy, 1.0f, -1);
	}
	/**
	 * Initializes probability matrices with uniform error model and sets initial barcode frequencies.
	 * Creates symmetric error model where correct base calls have probability correctCall and
	 * each incorrect call has probability (1-correctCall)/3. Initializes barcode frequencies
	 * based on expected status, with automatic calculation of unexpected barcode frequency
	 * from base recombination rate if not specified.
	 * @param correctCall Probability of correct base calling (typically 0.8-0.9)
	 * @param goodBarcode Initial frequency for expected barcodes (typically 1.0)
	 * @param badBarcode Initial frequency for unexpected barcodes (-1 for auto-calculation)
	 */
	public final void initializeProbs(float correctCall, float goodBarcode, float badBarcode){
		writelock();
		final float incorrectCall=(1-correctCall)/3f; //Split error probability equally among 3 wrong bases
		for(int pos=0; pos<probs.length; pos++){
			for(int xc=0; xc<5; xc++){
				for(int xr=0; xr<4; xr++){
					probs[pos][xc][xr]=incorrectCall; //Default to error probability
				}
				if(xc<4){probs[pos][xc][xc]=correctCall;} //Correct match has higher probability
			}
		}
		
		if(badBarcode<=0){ //Auto-calculate unexpected barcode frequency
			badBarcode=(baseRecombinationRate*expectedList.size())/allCodes.length; //Proportional to recombination rate
//			System.err.println(frequency);
			badBarcode=Tools.mid(badBarcode, minUnexpectedFrequency0, 1f); //Apply bounds
//			System.err.println(frequency+", "+minUnexpectedFrequency0);
		}
		
		comboFrequency=new float[allCodes.length];
		for(int i=0; i<allCodes.length; i++){
			Barcode b=allCodes[i];
			comboFrequency[i]=b.frequency=(b.expected==1 ? goodBarcode : badBarcode); //Assign frequency based on expected status
		}
		writeunlock();
	}
	
	/**
	 * Converts base transition count matrices to probability matrices using maximum likelihood
	 * estimation with Laplace smoothing. Applies pseudocount of +1 to prevent zero probabilities
	 * and enforces minimum probability thresholds to maintain numerical stability. Ensures
	 * correct base matches meet higher minimum probability than substitution errors.
	 * @param counts 3D count matrix [position][called_base][reference_base]
	 * @param probs Output 3D probability matrix to be populated
	 */
	public static final void fillProbs(long[][][] counts, float[][][] probs){
		for(int pos=0; pos<counts.length; pos++){
			for(int xq=0; xq<5; xq++){
//				final byte q=numberToBase[xq];
				final long[] crow=counts[pos][xq]; //Count row for this called base
				final float[] prow=probs[pos][xq]; //Probability row to populate
				final long sum=crow[0]+crow[1]+crow[2]+crow[3]+1; //Ignores noref, add pseudocount
				final float mult=1.0f/(Tools.max(sum, 1)); //Normalization factor
				for(int xr=0; xr<prow.length; xr++){
					prow[xr]=Tools.max(minBaseProb, (crow[xr]+1)*mult); //MLE with Laplace smoothing
				}
				if(xq<4){prow[xq]=Tools.max(minCorrectBaseProb, prow[xq]);} //Ensure minimum for correct matches
			}
		}
	}

	/*--------------------------------------------------------------*/
	/*----------------          Populating          ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Placeholder method for populating unexpected barcodes.
	 * Not implemented in this abstract class - subclasses should override if needed.
	 */
	@Override
	public final void populateUnexpected(){
		assert(false) : "Not valid.";
		//populateUnexpected(-1);
	}
	
	//These caused massive speed issues on the first refine cycle (100x slowdown).
//	final DoubleList getDoubleList(int side) {
//		DoubleList list=localDoubleLists[side].get();
//		if(list==null) {
//			list=new DoubleList(splitBytes[side].length);
//			localDoubleLists[side].set(list);
//		}
//		return list;
//	}
//	
//	final IntList getIntList() {
//		IntList list=localI.get();
//		if(list==null) {
//			list=new IntList();
//			localI.set(list);
//		}
//		return list;
//	}
	
	/** License validation check for closed-source functionality */
	protected final boolean valid(){return fun.Dongle.check(this);}
	
	/** Returns false as this is server-side processing code */
	public static final boolean clientside(){return false;}
	
	/*--------------------------------------------------------------*/
	
	/** 3D probability matrix [position][called_base A,C,G,T,N][reference_base A,C,G,T] */
	final float[][][] probs;
	
	/** Cached frequency values for all barcode combinations, indexed by allCodes array */
	float[] comboFrequency;
	
	/*--------------------------------------------------------------*/

	/** Maximum hamming distance allowed during refinement phase to control search space and prevent over-assignment */
	static int maxHDist0=6;
	/** Minimum ratio between best and second-best scores during refinement to avoid ambiguous assignments */
	static float minRatio0=20f;
	/** Minimum probability threshold for refinement phase (log scale: -12 = 10^-12 absolute probability) */
	static float minProb0=-12; //Possibly 16 for fbranch=0
	
	/** Number of EM refinement iterations - typically converges within 4-5 passes for most datasets */
	static int passes0=5; //4 is probably sufficient; for the main dataset it converged there (to 4 significant digits)

	/** Maximum hamming distance allowed during final assignment phase - stricter than refinement */
	static int maxHDist1=6;
	/** Minimum ratio between best and second-best scores for final assignment - much stricter than refinement */
	static float minRatio1=1_000_000f; //Possibly 5k for single-ended (10bp), or based on length
	/** Minimum probability threshold for final assignment phase (log scale: -5.6 = ~2.5×10^-6 absolute probability) */
	static float minProb1=-5.6f; //Possibly 5.5 for fbranch0; 3.4 for fbranch1 single
	//minprob1=-5.2 misses 80% of phix, which is recovered at -6.0.  It only has hdist 3.
//	static float minProbB1=-5.5f;

//	private final ThreadLocal<DoubleList> leftLocalF=new ThreadLocal<DoubleList>();
//	private final ThreadLocal<DoubleList> rightLocalF=new ThreadLocal<DoubleList>();
//	private final ThreadLocal<byte[]> leftLocalB=new ThreadLocal<byte[]>();
//	private final ThreadLocal<byte[]> rightLocalB=new ThreadLocal<byte[]>();
//	@SuppressWarnings("unchecked")
//	private final ThreadLocal<DoubleList>[] localDoubleLists=new ThreadLocal[] {leftLocalF, rightLocalF};
//	
//
//	private final ThreadLocal<IntList> localI=new ThreadLocal<IntList>();
//	@SuppressWarnings("unchecked")
//	private final ThreadLocal<byte[]>[] localByteArrays=new ThreadLocal[] {leftLocalB, rightLocalB};
	
	/*--------------------------------------------------------------*/
	
	/** Pseudocount added to barcode frequencies to prevent zero-probability assignments and handle rare sequences */
	static int spoofReads=4; //4 or even 16 seemed good for a full lane 89-way but maybe not for small datasets
	
	/** Base recombination rate for calculating unexpected barcode frequencies from PCR cross-contamination */
	static float baseRecombinationRate=0.08f; //This is a bit high; measured at 0.033. However, since it is nonuniform, a high value is safer
	/** Minimum frequency floor for unexpected barcodes during probability matrix initialization */
	static float minUnexpectedFrequency0=0.00002f; //Normally overridden by baseRecombinationRate
	/** Minimum frequency floor for unexpected barcodes during runtime frequency updates */
	static float minUnexpectedFrequency=0.0000001f; //Normally overridden by spoofReads
	/** Penalty multiplier applied to unexpected barcode frequencies to discourage spurious assignments */
	static float unexpectedMult=1f;
	
	/** Minimum probability floor for any base transition to ensure numerical stability and prevent log(0) errors */
	static float minBaseProb=0.0001f;
	/** Minimum probability floor for correct base matches - must be higher than error probabilities */
	static float minCorrectBaseProb=0.4f;
	
	//These actually has no impact since perfect matches are always assigned if hybridhd>=0
	
	/** Soft frequency floor for expected barcodes in highly unbalanced libraries where some get very few reads */
	static float minExpectedFrequency=0.02f; //This is for highly unbalanced libraries where one gets hardly any reads
	/** Semi-hard score threshold to prevent discarding perfect matches of expected barcodes */
	static float minExpectedScore=0.001f; //This is to prevent exact matches from being discarded
	/** Scoring ratio multiplier applied to expected barcode matches for preferential treatment */
	static float expectedRatio1=10000f;
	/** Whether to apply square-root transformation to expected barcode frequencies to compress dynamic range */
	static boolean sqrtFrequency=true;

	/** Base calling accuracy assumption for initial probability matrix - typically 82% for Illumina */
	protected static float accuracy=0.82f;
	/** Branching factor controlling search algorithm complexity (0=single-threaded, 1=dual-threaded) */
	protected static int FBRANCH=1;
	/** Weight factor balancing global vs tile-specific probability estimates */
	protected static float TILE_WEIGHT=3f;
	
	/** Whether to split barcode lists during processing - optimization flag with minimal impact */
	protected static boolean splitList=false; //Not needed for speed or synchronization; can be removed
	/** Hamming distance threshold for hybrid matching mode that allows some errors in exact matches */
	protected static int hybridHDist=1;
	/** Clear zone radius around hybrid matches to prevent assignment conflicts and edge effects */
	protected static int hybridClearzone=2; //2 seems best...
	
}
