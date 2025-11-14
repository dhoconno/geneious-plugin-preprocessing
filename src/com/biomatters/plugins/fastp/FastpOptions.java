package com.biomatters.plugins.fastp;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.Options;

/**
 * Options panel for configuring fastp/fastplong parameters.
 *
 * This class creates the user interface for configuring all fastp parameters
 * before running the quality control operation.
 *
 * Main parameter categories:
 * 1. Tool Selection
 *    - Auto-detect (choose fastp vs fastplong based on read length)
 *    - Force fastp (short reads)
 *    - Force fastplong (long reads)
 *
 * 2. Input/Output Options
 *    - Compression level
 *    - Output format options
 *
 * 3. Quality Filtering
 *    - Qualified quality phred (default: 15)
 *    - Unqualified percent limit (default: 40)
 *    - N base limit (default: 5)
 *    - Average quality requirement (default: 0, disabled)
 *
 * 4. Length Filtering
 *    - Length required (minimum length, default: 15)
 *    - Length limit (maximum length, default: 0, disabled)
 *
 * 5. Adapter Trimming
 *    - Enable auto-detection (default: true)
 *    - Custom adapter sequence for R1
 *    - Custom adapter sequence for R2
 *    - Adapter trimming options
 *
 * 6. Per-read Cutting by Quality
 *    - Cut front (enable/disable)
 *    - Cut tail (enable/disable)
 *    - Cut window size
 *    - Cut mean quality
 *
 * 7. Base Correction (for paired-end)
 *    - Enable base correction in overlapped regions
 *
 * 8. UMI Processing
 *    - Enable UMI processing
 *    - UMI location
 *    - UMI length
 *
 * 9. Filtering Options
 *    - Complexity filtering
 *    - Low complexity threshold
 *
 * 10. Long-read Specific Options (fastplong)
 *     - Different quality thresholds optimized for long reads
 *     - Different adapter detection strategies
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpOptions extends Options {

    // Option keys - used to retrieve values later
    private static final String TOOL_MODE = "toolMode";
    private static final String THREADS = "threads";

    // Quality filtering options
    private static final String QUALIFIED_QUALITY_PHRED = "qualifiedQualityPhred";
    private static final String UNQUALIFIED_PERCENT_LIMIT = "unqualifiedPercentLimit";
    private static final String N_BASE_LIMIT = "nBaseLimit";
    private static final String AVERAGE_QUAL = "averageQual";

    // Length filtering options
    private static final String LENGTH_REQUIRED = "lengthRequired";
    private static final String LENGTH_LIMIT = "lengthLimit";

    // Adapter options
    private static final String DETECT_ADAPTER = "detectAdapter";
    private static final String ADAPTER_R1 = "adapterR1";
    private static final String ADAPTER_R2 = "adapterR2";

    // Cutting options
    private static final String CUT_FRONT = "cutFront";
    private static final String CUT_TAIL = "cutTail";
    private static final String CUT_RIGHT = "cutRight";
    private static final String CUT_WINDOW_SIZE = "cutWindowSize";
    private static final String CUT_MEAN_QUALITY = "cutMeanQuality";

    // Trimming options
    private static final String TRIM_FRONT1 = "trimFront1";
    private static final String TRIM_TAIL1 = "trimTail1";
    private static final String TRIM_FRONT2 = "trimFront2";
    private static final String TRIM_TAIL2 = "trimTail2";

    // Polyg and Polyx options
    private static final String TRIM_POLY_G = "trimPolyG";
    private static final String POLY_G_MIN_LEN = "polyGMinLen";
    private static final String TRIM_POLY_X = "trimPolyX";
    private static final String POLY_X_MIN_LEN = "polyXMinLen";

    // Paired-end options
    private static final String CORRECTION = "correction";
    private static final String OVERLAP_LEN_REQUIRE = "overlapLenRequire";
    private static final String OVERLAP_DIFF_LIMIT = "overlapDiffLimit";

    // UMI options
    private static final String UMI_ENABLED = "umiEnabled";
    private static final String UMI_LOC = "umiLoc";
    private static final String UMI_LENGTH = "umiLength";
    private static final String UMI_PREFIX = "umiPrefix";
    private static final String UMI_SKIP = "umiSkip";

    // Filtering options
    private static final String COMPLEXITY_FILTER = "complexityFilter";
    private static final String COMPLEXITY_THRESHOLD = "complexityThreshold";

    // Deduplication options
    private static final String DEDUP_ENABLED = "dedupEnabled";

    // Output options
    private static final String COMPRESSION_LEVEL = "compressionLevel";
    private static final String DISABLE_QUALITY_FILTERING = "disableQualityFiltering";
    private static final String DISABLE_LENGTH_FILTERING = "disableLengthFiltering";
    private static final String DISABLE_ADAPTER_TRIMMING = "disableAdapterTrimming";

    // Option field references
    private ComboBoxOption<OptionValue> toolModeOption;
    private IntegerOption threadsOption;

    // Quality filtering
    private IntegerOption qualifiedQualityPhredOption;
    private IntegerOption unqualifiedPercentLimitOption;
    private IntegerOption nBaseLimitOption;
    private IntegerOption averageQualOption;

    // Length filtering
    private IntegerOption lengthRequiredOption;
    private IntegerOption lengthLimitOption;

    // Adapter options
    private BooleanOption detectAdapterOption;
    private StringOption adapterR1Option;
    private StringOption adapterR2Option;

    // Cutting options
    private BooleanOption cutFrontOption;
    private BooleanOption cutTailOption;
    private BooleanOption cutRightOption;
    private IntegerOption cutWindowSizeOption;
    private IntegerOption cutMeanQualityOption;

    // Trimming options
    private IntegerOption trimFront1Option;
    private IntegerOption trimTail1Option;
    private IntegerOption trimFront2Option;
    private IntegerOption trimTail2Option;

    // Poly G/X options
    private BooleanOption trimPolyGOption;
    private IntegerOption polyGMinLenOption;
    private BooleanOption trimPolyXOption;
    private IntegerOption polyXMinLenOption;

    // Paired-end options
    private BooleanOption correctionOption;
    private IntegerOption overlapLenRequireOption;
    private IntegerOption overlapDiffLimitOption;

    // UMI options
    private BooleanOption umiEnabledOption;
    private ComboBoxOption<OptionValue> umiLocOption;
    private IntegerOption umiLengthOption;
    private StringOption umiPrefixOption;
    private IntegerOption umiSkipOption;

    // Filtering options
    private BooleanOption complexityFilterOption;
    private IntegerOption complexityThresholdOption;

    // Deduplication
    private BooleanOption dedupEnabledOption;

    // Output options
    private IntegerOption compressionLevelOption;
    private BooleanOption disableQualityFilteringOption;
    private BooleanOption disableLengthFilteringOption;
    private BooleanOption disableAdapterTrimmingOption;

    // Tool mode values
    private static final OptionValue TOOL_AUTO = new OptionValue("auto", "Auto-detect based on read length");
    private static final OptionValue TOOL_FASTP = new OptionValue("fastp", "Fastp (short reads)");
    private static final OptionValue TOOL_FASTPLONG = new OptionValue("fastplong", "Fastplong (long reads)");
    private static final OptionValue[] TOOL_MODES = new OptionValue[]{TOOL_AUTO, TOOL_FASTP, TOOL_FASTPLONG};

    // UMI location values
    private static final OptionValue UMI_LOC_NONE = new OptionValue("none", "None");
    private static final OptionValue UMI_LOC_INDEX1 = new OptionValue("index1", "Index 1");
    private static final OptionValue UMI_LOC_INDEX2 = new OptionValue("index2", "Index 2");
    private static final OptionValue UMI_LOC_READ1 = new OptionValue("read1", "Read 1");
    private static final OptionValue UMI_LOC_READ2 = new OptionValue("read2", "Read 2");
    private static final OptionValue UMI_LOC_PER_INDEX = new OptionValue("per_index", "Per Index");
    private static final OptionValue UMI_LOC_PER_READ = new OptionValue("per_read", "Per Read");
    private static final OptionValue[] UMI_LOCATIONS = new OptionValue[]{
        UMI_LOC_NONE, UMI_LOC_INDEX1, UMI_LOC_INDEX2, UMI_LOC_READ1,
        UMI_LOC_READ2, UMI_LOC_PER_INDEX, UMI_LOC_PER_READ
    };

    /**
     * Constructor that initializes the options panel.
     *
     * @param documents the documents that will be processed (used for auto-detection)
     */
    public FastpOptions(AnnotatedPluginDocument... documents) {
        super(FastpOptions.class);

        // TODO: Analyze documents to determine appropriate defaults
        // e.g., detect if paired-end, estimate read length, etc.
        boolean hasPairedEnd = detectPairedEnd(documents);
        boolean hasLongReads = detectLongReads(documents);

        initializeOptions(hasPairedEnd, hasLongReads);
    }

    /**
     * Detects if the input includes paired-end reads.
     *
     * @param documents input documents
     * @return true if paired-end detected
     */
    private boolean detectPairedEnd(AnnotatedPluginDocument... documents) {
        // TODO: Implement actual detection logic
        // For now, return false as a default
        return false;
    }

    /**
     * Detects if the input includes long reads.
     *
     * @param documents input documents
     * @return true if long reads detected
     */
    private boolean detectLongReads(AnnotatedPluginDocument... documents) {
        // TODO: Implement actual detection logic
        // For now, return false as a default
        return false;
    }

    /**
     * Initializes all option fields with appropriate defaults.
     *
     * @param hasPairedEnd whether paired-end reads are detected
     * @param hasLongReads whether long reads are detected
     */
    private void initializeOptions(boolean hasPairedEnd, boolean hasLongReads) {
        // ===== TOOL SELECTION SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Tool Selection</h3></html>");
        endAlignHorizontally();

        toolModeOption = addComboBoxOption(TOOL_MODE, "Analysis mode:", TOOL_MODES,
            hasLongReads ? TOOL_FASTPLONG : TOOL_AUTO);
        addLabel("<html><i>Auto-detect will choose fastp for reads &lt;1000bp, fastplong for longer reads</i></html>");

        addDivider("");

        // ===== BASIC OPTIONS SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Basic Options</h3></html>");
        endAlignHorizontally();

        threadsOption = addIntegerOption(THREADS, "Number of threads:", 4, 1, 32);
        threadsOption.setDescription("Number of worker threads to use (default: 4)");

        compressionLevelOption = addIntegerOption(COMPRESSION_LEVEL, "Compression level:", 4, 1, 9);
        compressionLevelOption.setDescription("Gzip compression level (1=fastest, 9=best compression)");

        addDivider("");

        // ===== QUALITY FILTERING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Quality Filtering</h3></html>");
        endAlignHorizontally();

        disableQualityFilteringOption = addBooleanOption(DISABLE_QUALITY_FILTERING,
            "Disable quality filtering", false);

        qualifiedQualityPhredOption = addIntegerOption(QUALIFIED_QUALITY_PHRED,
            "Qualified quality phred:", 15, 0, 40);
        qualifiedQualityPhredOption.setDescription(
            "The quality value that a base is qualified (default: 15)");

        unqualifiedPercentLimitOption = addIntegerOption(UNQUALIFIED_PERCENT_LIMIT,
            "Unqualified percent limit:", 40, 0, 100);
        unqualifiedPercentLimitOption.setDescription(
            "Percentage of bases allowed to be unqualified (default: 40)");

        nBaseLimitOption = addIntegerOption(N_BASE_LIMIT,
            "N base limit:", 5, 0, 100);
        nBaseLimitOption.setDescription(
            "Maximum number of N bases allowed in a read (default: 5)");

        averageQualOption = addIntegerOption(AVERAGE_QUAL,
            "Average quality (0=disabled):", 0, 0, 40);
        averageQualOption.setDescription(
            "If specified, reads with average quality below this will be filtered (0=disabled)");

        addDivider("");

        // ===== LENGTH FILTERING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Length Filtering</h3></html>");
        endAlignHorizontally();

        disableLengthFilteringOption = addBooleanOption(DISABLE_LENGTH_FILTERING,
            "Disable length filtering", false);

        lengthRequiredOption = addIntegerOption(LENGTH_REQUIRED,
            "Minimum length:", 15, 0, 10000);
        lengthRequiredOption.setDescription(
            "Reads shorter than this will be discarded (default: 15)");

        lengthLimitOption = addIntegerOption(LENGTH_LIMIT,
            "Maximum length (0=disabled):", 0, 0, 100000);
        lengthLimitOption.setDescription(
            "Reads longer than this will be discarded (0=no limit)");

        addDivider("");

        // ===== ADAPTER TRIMMING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Adapter Trimming</h3></html>");
        endAlignHorizontally();

        disableAdapterTrimmingOption = addBooleanOption(DISABLE_ADAPTER_TRIMMING,
            "Disable adapter trimming", false);

        detectAdapterOption = addBooleanOption(DETECT_ADAPTER,
            "Auto-detect adapters", true);
        detectAdapterOption.setDescription(
            "Enable adapter sequence auto-detection for both PE and SE data");

        adapterR1Option = addStringOption(ADAPTER_R1, "Custom adapter R1:", "");
        adapterR1Option.setDescription(
            "Custom adapter sequence for read 1 (overrides auto-detection)");

        adapterR2Option = addStringOption(ADAPTER_R2, "Custom adapter R2 (PE only):", "");
        adapterR2Option.setDescription(
            "Custom adapter sequence for read 2 (paired-end only)");

        // Only show R2 adapter option if paired-end is detected
        if (!hasPairedEnd) {
            adapterR2Option.setEnabled(false);
        }

        addDivider("");

        // ===== PER-READ QUALITY CUTTING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Per-read Quality Cutting</h3></html>");
        endAlignHorizontally();

        cutFrontOption = addBooleanOption(CUT_FRONT,
            "Cut front (5' end)", false);
        cutFrontOption.setDescription(
            "Enable quality cutting at the 5' end of reads");

        cutTailOption = addBooleanOption(CUT_TAIL,
            "Cut tail (3' end)", false);
        cutTailOption.setDescription(
            "Enable quality cutting at the 3' end of reads");

        cutRightOption = addBooleanOption(CUT_RIGHT,
            "Cut right (alternative 3' algorithm)", false);
        cutRightOption.setDescription(
            "Alternative quality cutting at 3' end using a different algorithm");

        cutWindowSizeOption = addIntegerOption(CUT_WINDOW_SIZE,
            "Cut window size:", 4, 1, 100);
        cutWindowSizeOption.setDescription(
            "Window size for quality cutting (default: 4)");

        cutMeanQualityOption = addIntegerOption(CUT_MEAN_QUALITY,
            "Cut mean quality:", 20, 0, 40);
        cutMeanQualityOption.setDescription(
            "Mean quality requirement for the sliding window (default: 20)");

        addDivider("");

        // ===== BASE TRIMMING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Base Trimming</h3></html>");
        endAlignHorizontally();

        trimFront1Option = addIntegerOption(TRIM_FRONT1,
            "Trim front bases (R1):", 0, 0, 100);
        trimFront1Option.setDescription(
            "Number of bases to trim from the 5' end of read 1");

        trimTail1Option = addIntegerOption(TRIM_TAIL1,
            "Trim tail bases (R1):", 0, 0, 100);
        trimTail1Option.setDescription(
            "Number of bases to trim from the 3' end of read 1");

        trimFront2Option = addIntegerOption(TRIM_FRONT2,
            "Trim front bases (R2):", 0, 0, 100);
        trimFront2Option.setDescription(
            "Number of bases to trim from the 5' end of read 2 (paired-end only)");

        trimTail2Option = addIntegerOption(TRIM_TAIL2,
            "Trim tail bases (R2):", 0, 0, 100);
        trimTail2Option.setDescription(
            "Number of bases to trim from the 3' end of read 2 (paired-end only)");

        // Only show R2 trimming options if paired-end is detected
        if (!hasPairedEnd) {
            trimFront2Option.setEnabled(false);
            trimTail2Option.setEnabled(false);
        }

        addDivider("");

        // ===== POLY-G AND POLY-X TRIMMING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>PolyG/PolyX Trimming</h3></html>");
        endAlignHorizontally();

        addLabel("<html><i>PolyG trimming is useful for Illumina NextSeq/NovaSeq data</i></html>");

        trimPolyGOption = addBooleanOption(TRIM_POLY_G,
            "Enable polyG tail trimming", false);
        trimPolyGOption.setDescription(
            "Trim polyG tails (useful for NextSeq/NovaSeq two-color chemistry)");

        polyGMinLenOption = addIntegerOption(POLY_G_MIN_LEN,
            "PolyG min length:", 10, 1, 100);
        polyGMinLenOption.setDescription(
            "Minimum length of polyG to trigger trimming (default: 10)");

        trimPolyXOption = addBooleanOption(TRIM_POLY_X,
            "Enable polyX tail trimming", false);
        trimPolyXOption.setDescription(
            "Trim polyX tails (any base repeated)");

        polyXMinLenOption = addIntegerOption(POLY_X_MIN_LEN,
            "PolyX min length:", 10, 1, 100);
        polyXMinLenOption.setDescription(
            "Minimum length of polyX to trigger trimming (default: 10)");

        addDivider("");

        // ===== PAIRED-END SPECIFIC OPTIONS =====
        if (hasPairedEnd) {
            beginAlignHorizontally("", false);
            addLabel("<html><h3>Paired-end Options</h3></html>");
            endAlignHorizontally();

            correctionOption = addBooleanOption(CORRECTION,
                "Base correction in overlapped regions", false);
            correctionOption.setDescription(
                "Enable base correction in overlapped regions for paired-end data");

            overlapLenRequireOption = addIntegerOption(OVERLAP_LEN_REQUIRE,
                "Overlap length requirement:", 30, 1, 1000);
            overlapLenRequireOption.setDescription(
                "Minimum overlap length to trigger base correction (default: 30)");

            overlapDiffLimitOption = addIntegerOption(OVERLAP_DIFF_LIMIT,
                "Overlap difference limit:", 5, 0, 100);
            overlapDiffLimitOption.setDescription(
                "Maximum number of mismatches allowed in overlap (default: 5)");

            addDivider("");
        }

        // ===== UMI PROCESSING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>UMI Processing</h3></html>");
        endAlignHorizontally();

        umiEnabledOption = addBooleanOption(UMI_ENABLED,
            "Enable UMI processing", false);
        umiEnabledOption.setDescription(
            "Enable Unique Molecular Identifier (UMI) processing");

        umiLocOption = addComboBoxOption(UMI_LOC, "UMI location:",
            UMI_LOCATIONS, UMI_LOC_NONE);
        umiLocOption.setDescription(
            "Specify the location of UMI sequences");

        umiLengthOption = addIntegerOption(UMI_LENGTH,
            "UMI length:", 0, 0, 100);
        umiLengthOption.setDescription(
            "Length of UMI sequence");

        umiPrefixOption = addStringOption(UMI_PREFIX, "UMI prefix:", "");
        umiPrefixOption.setDescription(
            "Prefix to add to UMI in read name");

        umiSkipOption = addIntegerOption(UMI_SKIP,
            "UMI skip bases:", 0, 0, 100);
        umiSkipOption.setDescription(
            "Number of bases to skip after UMI");

        addDivider("");

        // ===== ADVANCED FILTERING SECTION =====
        beginAlignHorizontally("", false);
        addLabel("<html><h3>Advanced Filtering</h3></html>");
        endAlignHorizontally();

        complexityFilterOption = addBooleanOption(COMPLEXITY_FILTER,
            "Enable complexity filter", false);
        complexityFilterOption.setDescription(
            "Filter low complexity reads (e.g., AAAAA...)");

        complexityThresholdOption = addIntegerOption(COMPLEXITY_THRESHOLD,
            "Complexity threshold:", 30, 0, 100);
        complexityThresholdOption.setDescription(
            "Percentage threshold for complexity filtering (default: 30)");

        dedupEnabledOption = addBooleanOption(DEDUP_ENABLED,
            "Enable deduplication", false);
        dedupEnabledOption.setDescription(
            "Enable duplicate read removal (exact sequence matching)");
    }

    // ===== GETTER METHODS =====

    /**
     * Gets the selected tool mode.
     * @return "auto", "fastp", or "fastplong"
     */
    public String getToolMode() {
        return toolModeOption.getValue().getName();
    }

    /**
     * Gets the number of threads to use.
     * @return thread count
     */
    public int getThreads() {
        return threadsOption.getValue();
    }

    /**
     * Gets the compression level.
     * @return compression level (1-9)
     */
    public int getCompressionLevel() {
        return compressionLevelOption.getValue();
    }

    /**
     * Checks if quality filtering is disabled.
     * @return true if quality filtering is disabled
     */
    public boolean isQualityFilteringDisabled() {
        return disableQualityFilteringOption.getValue();
    }

    /**
     * Gets the qualified quality phred threshold.
     * @return quality threshold
     */
    public int getQualifiedQualityPhred() {
        return qualifiedQualityPhredOption.getValue();
    }

    /**
     * Gets the unqualified percent limit.
     * @return percent limit
     */
    public int getUnqualifiedPercentLimit() {
        return unqualifiedPercentLimitOption.getValue();
    }

    /**
     * Gets the N base limit.
     * @return N base limit
     */
    public int getNBaseLimit() {
        return nBaseLimitOption.getValue();
    }

    /**
     * Gets the average quality requirement.
     * @return average quality (0 if disabled)
     */
    public int getAverageQuality() {
        return averageQualOption.getValue();
    }

    /**
     * Checks if length filtering is disabled.
     * @return true if length filtering is disabled
     */
    public boolean isLengthFilteringDisabled() {
        return disableLengthFilteringOption.getValue();
    }

    /**
     * Gets the minimum required length.
     * @return minimum length
     */
    public int getLengthRequired() {
        return lengthRequiredOption.getValue();
    }

    /**
     * Gets the maximum length limit.
     * @return maximum length (0 if disabled)
     */
    public int getLengthLimit() {
        return lengthLimitOption.getValue();
    }

    /**
     * Checks if adapter trimming is disabled.
     * @return true if adapter trimming is disabled
     */
    public boolean isAdapterTrimmingDisabled() {
        return disableAdapterTrimmingOption.getValue();
    }

    /**
     * Checks if adapter detection is enabled.
     * @return true if auto-detect is enabled
     */
    public boolean isDetectAdapterEnabled() {
        return detectAdapterOption.getValue();
    }

    /**
     * Gets the custom adapter sequence for R1.
     * @return adapter sequence or empty string
     */
    public String getAdapterR1() {
        return adapterR1Option.getValue();
    }

    /**
     * Gets the custom adapter sequence for R2.
     * @return adapter sequence or empty string
     */
    public String getAdapterR2() {
        return adapterR2Option.getValue();
    }

    /**
     * Checks if front cutting is enabled.
     * @return true if enabled
     */
    public boolean isCutFrontEnabled() {
        return cutFrontOption.getValue();
    }

    /**
     * Checks if tail cutting is enabled.
     * @return true if enabled
     */
    public boolean isCutTailEnabled() {
        return cutTailOption.getValue();
    }

    /**
     * Checks if right cutting is enabled.
     * @return true if enabled
     */
    public boolean isCutRightEnabled() {
        return cutRightOption.getValue();
    }

    /**
     * Gets the cut window size.
     * @return window size
     */
    public int getCutWindowSize() {
        return cutWindowSizeOption.getValue();
    }

    /**
     * Gets the cut mean quality.
     * @return mean quality
     */
    public int getCutMeanQuality() {
        return cutMeanQualityOption.getValue();
    }

    /**
     * Gets the number of bases to trim from front of R1.
     * @return number of bases
     */
    public int getTrimFront1() {
        return trimFront1Option.getValue();
    }

    /**
     * Gets the number of bases to trim from tail of R1.
     * @return number of bases
     */
    public int getTrimTail1() {
        return trimTail1Option.getValue();
    }

    /**
     * Gets the number of bases to trim from front of R2.
     * @return number of bases
     */
    public int getTrimFront2() {
        return trimFront2Option.getValue();
    }

    /**
     * Gets the number of bases to trim from tail of R2.
     * @return number of bases
     */
    public int getTrimTail2() {
        return trimTail2Option.getValue();
    }

    /**
     * Checks if polyG trimming is enabled.
     * @return true if enabled
     */
    public boolean isTrimPolyGEnabled() {
        return trimPolyGOption.getValue();
    }

    /**
     * Gets the minimum polyG length.
     * @return minimum length
     */
    public int getPolyGMinLen() {
        return polyGMinLenOption.getValue();
    }

    /**
     * Checks if polyX trimming is enabled.
     * @return true if enabled
     */
    public boolean isTrimPolyXEnabled() {
        return trimPolyXOption.getValue();
    }

    /**
     * Gets the minimum polyX length.
     * @return minimum length
     */
    public int getPolyXMinLen() {
        return polyXMinLenOption.getValue();
    }

    /**
     * Checks if base correction is enabled.
     * @return true if enabled
     */
    public boolean isCorrectionEnabled() {
        return correctionOption != null && correctionOption.getValue();
    }

    /**
     * Gets the overlap length requirement.
     * @return overlap length
     */
    public int getOverlapLenRequire() {
        return overlapLenRequireOption != null ? overlapLenRequireOption.getValue() : 30;
    }

    /**
     * Gets the overlap difference limit.
     * @return overlap difference limit
     */
    public int getOverlapDiffLimit() {
        return overlapDiffLimitOption != null ? overlapDiffLimitOption.getValue() : 5;
    }

    /**
     * Checks if UMI processing is enabled.
     * @return true if enabled
     */
    public boolean isUmiEnabled() {
        return umiEnabledOption.getValue();
    }

    /**
     * Gets the UMI location.
     * @return UMI location name
     */
    public String getUmiLocation() {
        return umiLocOption.getValue().getName();
    }

    /**
     * Gets the UMI length.
     * @return UMI length
     */
    public int getUmiLength() {
        return umiLengthOption.getValue();
    }

    /**
     * Gets the UMI prefix.
     * @return UMI prefix
     */
    public String getUmiPrefix() {
        return umiPrefixOption.getValue();
    }

    /**
     * Gets the UMI skip bases.
     * @return number of bases to skip
     */
    public int getUmiSkip() {
        return umiSkipOption.getValue();
    }

    /**
     * Checks if complexity filtering is enabled.
     * @return true if enabled
     */
    public boolean isComplexityFilterEnabled() {
        return complexityFilterOption.getValue();
    }

    /**
     * Gets the complexity threshold.
     * @return complexity threshold
     */
    public int getComplexityThreshold() {
        return complexityThresholdOption.getValue();
    }

    /**
     * Checks if deduplication is enabled.
     * @return true if enabled
     */
    public boolean isDedupEnabled() {
        return dedupEnabledOption.getValue();
    }

    /**
     * Validates the options before running the operation.
     *
     * @return null if valid, error message if invalid
     */
    public String validateOptions() {
        // Validate adapter sequences if provided
        String adapterR1 = getAdapterR1();
        if (!adapterR1.isEmpty() && !isValidDnaSequence(adapterR1)) {
            return "Invalid adapter sequence for R1. Must contain only A, T, G, C, N characters.";
        }

        String adapterR2 = getAdapterR2();
        if (!adapterR2.isEmpty() && !isValidDnaSequence(adapterR2)) {
            return "Invalid adapter sequence for R2. Must contain only A, T, G, C, N characters.";
        }

        // Validate UMI options
        if (isUmiEnabled()) {
            if (getUmiLocation().equals("none")) {
                return "UMI processing is enabled but no UMI location is specified.";
            }
            if (getUmiLength() <= 0) {
                return "UMI length must be greater than 0 when UMI processing is enabled.";
            }
        }

        // Validate quality filtering options
        if (!isQualityFilteringDisabled()) {
            if (getQualifiedQualityPhred() < 0 || getQualifiedQualityPhred() > 40) {
                return "Qualified quality phred must be between 0 and 40.";
            }
            if (getUnqualifiedPercentLimit() < 0 || getUnqualifiedPercentLimit() > 100) {
                return "Unqualified percent limit must be between 0 and 100.";
            }
        }

        // Validate length filtering options
        if (!isLengthFilteringDisabled()) {
            if (getLengthRequired() < 0) {
                return "Minimum length cannot be negative.";
            }
            if (getLengthLimit() > 0 && getLengthLimit() < getLengthRequired()) {
                return "Maximum length cannot be less than minimum length.";
            }
        }

        return null; // All validations passed
    }

    /**
     * Validates if a string is a valid DNA sequence.
     *
     * @param sequence the sequence to validate
     * @return true if valid
     */
    private boolean isValidDnaSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return true;
        }
        return sequence.toUpperCase().matches("[ATGCN]+");
    }
}
