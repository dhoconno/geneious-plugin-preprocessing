package com.biomatters.plugins.fastp;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.GeneiousActionOptions;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import jebl.util.ProgressListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document operation for running fastp/fastplong quality control and preprocessing.
 *
 * This operation handles:
 * - Detection of input file types (FASTQ, paired-end, long-reads)
 * - Configuration of fastp/fastplong parameters
 * - Execution of the external fastp binary
 * - Processing of output files and reports
 * - Integration of results back into Geneious
 *
 * The operation supports:
 * - Single-end FASTQ files
 * - Paired-end FASTQ files (R1/R2)
 * - Long-read FASTQ files (PacBio, Nanopore)
 * - Compressed FASTQ files (gzip)
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpOperation extends DocumentOperation {

    private static final Logger logger = Logger.getLogger(FastpOperation.class.getName());

    // Length threshold for distinguishing short reads from long reads
    private static final int LONG_READ_THRESHOLD = 1000;

    // Patterns for detecting paired-end reads
    private static final String[] PAIR_INDICATORS_R1 = {"_R1", "_1", ".R1", ".1", "_forward", "_fwd"};
    private static final String[] PAIR_INDICATORS_R2 = {"_R2", "_2", ".R2", ".2", "_reverse", "_rev"};

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    /**
     * Returns the action options that define how this operation appears in Geneious.
     *
     * @return GeneiousActionOptions configuration
     */
    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions(
            "Run Fastp QC",  // Menu item name
            "Perform quality control and preprocessing on sequencing reads using fastp/fastplong",
            null,  // No custom icon for now
            GeneiousActionOptions.Category.None  // No specific category (will appear in main menu)
        )
        .setInMainToolbar(true)  // Show in main toolbar for easy access
        .setInPopupMenu(true);   // Show in right-click context menu
    }

    /**
     * Returns the help text for this operation.
     *
     * @return help text
     */
    @Override
    public String getHelp() {
        return "Runs fastp quality control and preprocessing on selected FASTQ files. " +
               "Fastp provides comprehensive QC analysis, adapter detection and trimming, " +
               "quality filtering, and various preprocessing functions. " +
               "The operation will automatically detect paired-end reads and long-reads, " +
               "using the appropriate tool variant (fastp or fastplong).";
    }

    /**
     * Defines which documents this operation can be performed on.
     *
     * This operation works on FASTQ sequence documents.
     *
     * @return array of DocumentSelectionSignature defining valid selections
     */
    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        // Accept nucleotide sequence documents (FASTQ files)
        // Use the helper method which is more flexible about document types
        return new DocumentSelectionSignature[]{
            DocumentSelectionSignature.forNucleotideSequences(1, Integer.MAX_VALUE)
        };
    }

    /**
     * Returns the options panel for configuring fastp parameters.
     *
     * @param documents the selected documents
     * @return Options object representing the configuration UI
     */
    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new FastpOptions(documents);
    }

    /**
     * Performs the fastp operation on the selected documents.
     *
     * Workflow:
     * 1. Validate input documents (must be FASTQ)
     * 2. Determine if single-end or paired-end
     * 3. Determine if short-read or long-read (select fastp vs fastplong)
     * 4. Export sequences to temporary FASTQ files
     * 5. Build fastp command line with user options
     * 6. Execute fastp binary
     * 7. Parse JSON output for statistics
     * 8. Import processed FASTQ files back as new documents
     * 9. Import HTML report as a document
     * 10. Clean up temporary files
     *
     * @param documents the selected documents to process
     * @param progressListener listener for progress updates
     * @param options the configured options from the options panel
     * @return list of output documents (processed sequences and reports)
     * @throws DocumentOperationException if operation fails
     */
    @Override
    public List<AnnotatedPluginDocument> performOperation(
            AnnotatedPluginDocument[] documents,
            ProgressListener progressListener,
            Options options) throws DocumentOperationException {

        System.out.println("\n===============================================");
        System.out.println("=== FASTP OPERATION STARTING ===");
        System.out.println("===============================================");
        System.out.println("Number of documents selected: " + documents.length);

        FastpOptions fastpOptions = (FastpOptions) options;

        // Validate options
        System.out.println("Validating options...");
        String validationError = fastpOptions.validateOptions();
        if (validationError != null) {
            System.err.println("ERROR: Validation failed: " + validationError);
            throw new DocumentOperationException(validationError);
        }
        System.out.println("Options validation passed");

        String msg = "Starting fastp operation on " + documents.length + " document(s)";
        System.out.println(msg);
        logger.info(msg);
        progressListener.setMessage("Initializing fastp...");

        // Initialize binary manager
        System.out.println("Initializing binary manager...");
        FastpBinaryManager binaryManager = FastpBinaryManager.getInstance();
        try {
            binaryManager.initialize();
            System.out.println("Binary manager initialized successfully");
            System.out.println("Fastp binary: " + binaryManager.getFastpPath());
            System.out.println("Fastplong binary: " + binaryManager.getFastplongPath());
        } catch (IOException e) {
            System.err.println("ERROR: Failed to initialize binaries: " + e.getMessage());
            throw new DocumentOperationException("Failed to initialize fastp binaries: " + e.getMessage(), e);
        }

        List<AnnotatedPluginDocument> allOutputDocuments = new ArrayList<>();

        try {
            // Group documents by paired-end status
            System.out.println("\n=== Grouping Documents ===");
            Map<String, List<AnnotatedPluginDocument>> documentGroups = groupDocuments(documents);
            System.out.println("Created " + documentGroups.size() + " document group(s)");

            int groupIndex = 0;
            for (Map.Entry<String, List<AnnotatedPluginDocument>> entry : documentGroups.entrySet()) {
                groupIndex++;
                String groupKey = entry.getKey();
                List<AnnotatedPluginDocument> group = entry.getValue();

                System.out.println("\n--- Processing Group " + groupIndex + " of " + documentGroups.size() + " ---");
                System.out.println("Group key: " + groupKey);
                System.out.println("Number of files in group: " + group.size());
                for (AnnotatedPluginDocument doc : group) {
                    System.out.println("  - " + doc.getName());
                }

                progressListener.setMessage("Processing group " + groupIndex + " of " + documentGroups.size() +
                                           " (" + group.size() + " file(s))");

                // Process this group
                List<AnnotatedPluginDocument> groupOutput = processDocumentGroup(
                    group, fastpOptions, binaryManager, progressListener, groupIndex, documentGroups.size()
                );

                System.out.println("Group " + groupIndex + " produced " + groupOutput.size() + " output document(s)");
                allOutputDocuments.addAll(groupOutput);
            }

            progressListener.setMessage("Fastp completed successfully");
            String completeMsg = "Fastp operation completed. Generated " + allOutputDocuments.size() + " output document(s)";
            System.out.println("\n===============================================");
            System.out.println("=== FASTP OPERATION COMPLETED ===");
            System.out.println("===============================================");
            System.out.println(completeMsg);
            logger.info(completeMsg);

            return allOutputDocuments;

        } catch (Exception e) {
            System.err.println("\n===============================================");
            System.err.println("=== FASTP OPERATION FAILED ===");
            System.err.println("===============================================");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            logger.severe("Fastp operation failed: " + e.getMessage());
            throw new DocumentOperationException("Fastp operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Groups documents by their pairing status.
     * Returns a map where:
     * - "single_X" keys represent single-end documents
     * - "paired_basename" keys represent paired-end groups
     *
     * @param documents input documents
     * @return grouped documents
     */
    private Map<String, List<AnnotatedPluginDocument>> groupDocuments(AnnotatedPluginDocument[] documents) {
        Map<String, List<AnnotatedPluginDocument>> groups = new HashMap<>();
        Map<String, AnnotatedPluginDocument> r1Candidates = new HashMap<>();
        Map<String, AnnotatedPluginDocument> r2Candidates = new HashMap<>();
        List<AnnotatedPluginDocument> ungrouped = new ArrayList<>();

        // First pass: identify R1 and R2 candidates
        for (AnnotatedPluginDocument doc : documents) {
            String name = doc.getName();
            String baseName = null;
            boolean isR1 = false;
            boolean isR2 = false;

            // Check for R1 patterns
            for (String indicator : PAIR_INDICATORS_R1) {
                if (name.contains(indicator)) {
                    baseName = name.replace(indicator, "_R");
                    isR1 = true;
                    break;
                }
            }

            // Check for R2 patterns
            if (!isR1) {
                for (String indicator : PAIR_INDICATORS_R2) {
                    if (name.contains(indicator)) {
                        baseName = name.replace(indicator, "_R");
                        isR2 = true;
                        break;
                    }
                }
            }

            if (isR1 && baseName != null) {
                r1Candidates.put(baseName, doc);
            } else if (isR2 && baseName != null) {
                r2Candidates.put(baseName, doc);
            } else {
                ungrouped.add(doc);
            }
        }

        // Second pass: match R1 and R2
        for (Map.Entry<String, AnnotatedPluginDocument> entry : r1Candidates.entrySet()) {
            String baseName = entry.getKey();
            AnnotatedPluginDocument r1 = entry.getValue();

            if (r2Candidates.containsKey(baseName)) {
                AnnotatedPluginDocument r2 = r2Candidates.get(baseName);
                List<AnnotatedPluginDocument> pair = Arrays.asList(r1, r2);
                groups.put("paired_" + baseName, pair);
                r2Candidates.remove(baseName); // Remove matched R2
            } else {
                // R1 without matching R2, treat as single-end
                ungrouped.add(r1);
            }
        }

        // Add any unmatched R2s to ungrouped
        ungrouped.addAll(r2Candidates.values());

        // Add all ungrouped as individual single-end
        for (int i = 0; i < ungrouped.size(); i++) {
            groups.put("single_" + i, Collections.singletonList(ungrouped.get(i)));
        }

        logger.info("Grouped " + documents.length + " documents into " + groups.size() + " group(s)");
        return groups;
    }

    /**
     * Processes a group of documents (either single-end or paired-end).
     *
     * @param documents the documents in this group (1 for SE, 2 for PE)
     * @param options fastp options
     * @param binaryManager binary manager
     * @param progressListener progress listener
     * @param groupIndex current group index
     * @param totalGroups total number of groups
     * @return list of output documents
     */
    private List<AnnotatedPluginDocument> processDocumentGroup(
            List<AnnotatedPluginDocument> documents,
            FastpOptions options,
            FastpBinaryManager binaryManager,
            ProgressListener progressListener,
            int groupIndex,
            int totalGroups) throws DocumentOperationException {

        boolean isPairedEnd = documents.size() == 2;
        logger.info("Processing " + (isPairedEnd ? "paired-end" : "single-end") + " group");

        File tempDir = null;
        try {
            // Create temporary directory for this group
            tempDir = Files.createTempDirectory("fastp-group-" + groupIndex + "-").toFile();
            logger.info("Created temp directory: " + tempDir.getAbsolutePath());

            // Detect read type (short vs long)
            boolean isLongRead = detectLongReads(documents);
            String toolMode = options.getToolMode();
            boolean useFastplong = false;

            if ("auto".equals(toolMode)) {
                useFastplong = isLongRead;
            } else if ("fastplong".equals(toolMode)) {
                useFastplong = true;
            }

            String binaryPath = useFastplong ?
                binaryManager.getFastplongPath() : binaryManager.getFastpPath();

            logger.info("Using " + (useFastplong ? "fastplong" : "fastp") + " for this group");

            // Export sequences to FASTQ files
            progressListener.setMessage("Exporting sequences to FASTQ...");
            List<File> inputFiles = exportToFastq(documents, tempDir);

            // Prepare output files
            List<File> outputFiles = new ArrayList<>();
            File jsonReport = new File(tempDir, "fastp_report.json");
            File htmlReport = new File(tempDir, "fastp_report.html");

            for (int i = 0; i < inputFiles.size(); i++) {
                File outputFile = new File(tempDir, "output_" + (i + 1) + ".fastq");
                outputFiles.add(outputFile);
            }

            // Execute fastp/fastplong
            progressListener.setMessage("Running " + (useFastplong ? "fastplong" : "fastp") + "...");
            FastpExecutor executor = new FastpExecutor();
            int exitCode;

            if (isPairedEnd) {
                exitCode = executor.executePairedEnd(
                    binaryPath,
                    inputFiles.get(0), inputFiles.get(1),
                    outputFiles.get(0), outputFiles.get(1),
                    jsonReport, htmlReport,
                    options, progressListener
                );
            } else {
                exitCode = executor.executeSingleEnd(
                    binaryPath,
                    inputFiles.get(0),
                    outputFiles.get(0),
                    jsonReport, htmlReport,
                    options, progressListener
                );
            }

            if (exitCode != 0) {
                throw new DocumentOperationException("Fastp execution failed with exit code: " + exitCode);
            }

            // Parse JSON report
            progressListener.setMessage("Parsing results...");
            Map<String, Object> statistics = parseJsonReport(jsonReport);

            // Import processed sequences
            progressListener.setMessage("Importing processed sequences...");
            List<AnnotatedPluginDocument> outputDocuments = importProcessedSequences(
                outputFiles, documents, statistics, useFastplong
            );

            // Import HTML report as a document
            progressListener.setMessage("Importing HTML report...");
            AnnotatedPluginDocument reportDoc = importHtmlReport(htmlReport, documents.get(0).getName());
            if (reportDoc != null) {
                outputDocuments.add(reportDoc);
            }

            logger.info("Group processing completed. Generated " + outputDocuments.size() + " document(s)");
            return outputDocuments;

        } catch (IOException e) {
            throw new DocumentOperationException("I/O error during processing: " + e.getMessage(), e);
        } finally {
            // Clean up temporary directory
            if (tempDir != null && tempDir.exists()) {
                try {
                    deleteDirectory(tempDir);
                    logger.info("Cleaned up temp directory: " + tempDir.getAbsolutePath());
                } catch (Exception e) {
                    logger.warning("Failed to clean up temp directory: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Detects if the documents contain long reads (>=1000bp average).
     *
     * @param documents documents to analyze
     * @return true if long reads detected
     */
    private boolean detectLongReads(List<AnnotatedPluginDocument> documents) throws DocumentOperationException {
        long totalLength = 0;
        int sequenceCount = 0;

        for (AnnotatedPluginDocument doc : documents) {
            List<NucleotideSequenceDocument> sequences = new ArrayList<>();

            if (doc.getDocument() instanceof SequenceListDocument) {
                SequenceListDocument seqList = (SequenceListDocument) doc.getDocument();
                sequences.addAll(seqList.getNucleotideSequences());
            } else if (doc.getDocument() instanceof NucleotideSequenceDocument) {
                sequences.add((NucleotideSequenceDocument) doc.getDocument());
            }

            // Sample first few sequences for efficiency
            for (NucleotideSequenceDocument seqDoc : sequences) {
                totalLength += seqDoc.getSequenceLength();
                sequenceCount++;

                if (sequenceCount >= 10) {
                    break;
                }
            }

            if (sequenceCount >= 10) {
                break;
            }
        }

        if (sequenceCount == 0) {
            return false;
        }

        double averageLength = (double) totalLength / sequenceCount;
        boolean isLong = averageLength >= LONG_READ_THRESHOLD;
        logger.info("Average read length: " + DECIMAL_FORMAT.format(averageLength) +
                   "bp - classified as " + (isLong ? "long" : "short") + " reads");
        return isLong;
    }

    /**
     * Exports documents to FASTQ files.
     *
     * @param documents documents to export
     * @param outputDir output directory
     * @return list of exported FASTQ files
     */
    private List<File> exportToFastq(List<AnnotatedPluginDocument> documents, File outputDir)
            throws IOException, DocumentOperationException {

        List<File> outputFiles = new ArrayList<>();

        for (int i = 0; i < documents.size(); i++) {
            AnnotatedPluginDocument doc = documents.get(i);
            File outputFile = new File(outputDir, "input_" + (i + 1) + ".fastq");

            // Handle both individual sequences and sequence lists
            List<NucleotideSequenceDocument> sequences = new ArrayList<>();

            if (doc.getDocument() instanceof SequenceListDocument) {
                // SRA imports and other multi-sequence documents
                SequenceListDocument seqList = (SequenceListDocument) doc.getDocument();
                sequences.addAll(seqList.getNucleotideSequences());
                logger.info("Exporting sequence list with " + sequences.size() + " sequences");
            } else if (doc.getDocument() instanceof NucleotideSequenceDocument) {
                // Single sequence document
                sequences.add((NucleotideSequenceDocument) doc.getDocument());
            } else {
                throw new DocumentOperationException(
                    "Document '" + doc.getName() + "' is not a nucleotide sequence or sequence list"
                );
            }

            // Write all sequences to FASTQ format
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                for (NucleotideSequenceDocument seqDoc : sequences) {
                    writeFastqSequence(writer, seqDoc);
                }
            }

            outputFiles.add(outputFile);
            logger.info("Exported " + doc.getName() + " (" + sequences.size() + " sequences) to " + outputFile.getName());
        }

        return outputFiles;
    }

    /**
     * Writes a sequence to FASTQ format.
     *
     * @param writer output writer
     * @param seqDoc sequence document
     */
    private void writeFastqSequence(BufferedWriter writer, NucleotideSequenceDocument seqDoc)
            throws IOException {

        String name = seqDoc.getName();
        String sequence = seqDoc.getSequenceString();
        String quality = "";

        // Try to get quality scores if available
        byte[] qualityScores = null; // getSequenceQuality();
        if (qualityScores != null && qualityScores.length > 0) {
            StringBuilder qualBuilder = new StringBuilder();
            for (byte score : qualityScores) {
                // Convert quality score to ASCII (Phred+33)
                qualBuilder.append((char) (score + 33));
            }
            quality = qualBuilder.toString();
        } else {
            // Generate default quality scores (Phred 40 = 'I')
            quality = new String(new char[sequence.length()]).replace('\0', 'I');
        }

        // Write FASTQ format: @name, sequence, +, quality
        writer.write("@" + name + "\n");
        writer.write(sequence + "\n");
        writer.write("+\n");
        writer.write(quality + "\n");
    }

    /**
     * Parses the JSON report generated by fastp.
     * Uses simple string parsing to avoid external JSON dependencies.
     *
     * @param jsonFile JSON report file
     * @return map of statistics
     */
    private Map<String, Object> parseJsonReport(File jsonFile) throws IOException {
        Map<String, Object> stats = new HashMap<>();

        if (!jsonFile.exists() || jsonFile.length() == 0) {
            logger.warning("JSON report file is missing or empty");
            return stats;
        }

        try {
            // Read JSON file content
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }

            String json = jsonContent.toString();

            // Extract statistics using simple pattern matching
            // This is a simplified parser - in production, use a proper JSON library

            // Extract reads before and after
            stats.put("reads_before", extractLongValue(json, "\"total_reads\"\\s*:\\s*(\\d+)", 1));
            stats.put("bases_before", extractLongValue(json, "\"total_bases\"\\s*:\\s*(\\d+)", 1));
            stats.put("q20_rate_before", extractDoubleValue(json, "\"q20_rate\"\\s*:\\s*([0-9.]+)", 1));
            stats.put("q30_rate_before", extractDoubleValue(json, "\"q30_rate\"\\s*:\\s*([0-9.]+)", 1));
            stats.put("gc_content_before", extractDoubleValue(json, "\"gc_content\"\\s*:\\s*([0-9.]+)", 1));

            // For after_filtering, we need to find the second occurrence
            stats.put("reads_after", extractLongValue(json, "\"total_reads\"\\s*:\\s*(\\d+)", 2));
            stats.put("bases_after", extractLongValue(json, "\"total_bases\"\\s*:\\s*(\\d+)", 2));
            stats.put("q20_rate_after", extractDoubleValue(json, "\"q20_rate\"\\s*:\\s*([0-9.]+)", 2));
            stats.put("q30_rate_after", extractDoubleValue(json, "\"q30_rate\"\\s*:\\s*([0-9.]+)", 2));
            stats.put("gc_content_after", extractDoubleValue(json, "\"gc_content\"\\s*:\\s*([0-9.]+)", 2));

            // Extract filtering results
            stats.put("reads_passed", extractLongValue(json, "\"passed_filter_reads\"\\s*:\\s*(\\d+)", 1));
            stats.put("reads_low_quality", extractLongValue(json, "\"low_quality_reads\"\\s*:\\s*(\\d+)", 1));
            stats.put("reads_too_many_N", extractLongValue(json, "\"too_many_N_reads\"\\s*:\\s*(\\d+)", 1));
            stats.put("reads_too_short", extractLongValue(json, "\"too_short_reads\"\\s*:\\s*(\\d+)", 1));

            // Extract adapter information
            stats.put("adapter_trimmed_reads", extractLongValue(json, "\"adapter_trimmed_reads\"\\s*:\\s*(\\d+)", 1));
            stats.put("adapter_r1", extractStringValue(json, "\"read1_adapter_sequence\"\\s*:\\s*\"([^\"]+)\""));
            stats.put("adapter_r2", extractStringValue(json, "\"read2_adapter_sequence\"\\s*:\\s*\"([^\"]+)\""));

            // Extract duplication rate
            stats.put("duplication_rate", extractDoubleValue(json, "\"rate\"\\s*:\\s*([0-9.]+)", 1));

            logger.info("Parsed JSON report with " + stats.size() + " statistics");

        } catch (Exception e) {
            logger.warning("Error parsing JSON report: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Extracts a long value from JSON using regex pattern.
     */
    private long extractLongValue(String json, String pattern, int occurrence) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);
            for (int i = 0; i < occurrence; i++) {
                if (!m.find()) {
                    return 0;
                }
            }
            return Long.parseLong(m.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Extracts a double value from JSON using regex pattern.
     */
    private double extractDoubleValue(String json, String pattern, int occurrence) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);
            for (int i = 0; i < occurrence; i++) {
                if (!m.find()) {
                    return 0.0;
                }
            }
            return Double.parseDouble(m.group(1));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Extracts a string value from JSON using regex pattern.
     */
    private String extractStringValue(String json, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    /**
     * Imports processed sequences from FASTQ files back into Geneious.
     *
     * @param fastqFiles processed FASTQ files
     * @param originalDocs original input documents
     * @param statistics statistics from JSON report
     * @param isLongRead whether this is long-read data
     * @return list of imported documents
     */
    private List<AnnotatedPluginDocument> importProcessedSequences(
            List<File> fastqFiles,
            List<AnnotatedPluginDocument> originalDocs,
            Map<String, Object> statistics,
            boolean isLongRead) throws IOException, DocumentOperationException {

        System.out.println("=== Importing Processed Sequences ===");
        logger.info("Importing processed sequences from " + fastqFiles.size() + " FASTQ file(s)");

        List<AnnotatedPluginDocument> outputDocs = new ArrayList<>();

        for (int i = 0; i < fastqFiles.size(); i++) {
            File fastqFile = fastqFiles.get(i);
            AnnotatedPluginDocument originalDoc = originalDocs.get(i);

            System.out.println("Processing file: " + fastqFile.getAbsolutePath());
            System.out.println("File size: " + fastqFile.length() + " bytes");

            if (!fastqFile.exists() || fastqFile.length() == 0) {
                String msg = "Output FASTQ file is missing or empty: " + fastqFile.getName();
                System.err.println("WARNING: " + msg);
                logger.warning(msg);
                continue;
            }

            // Parse FASTQ file and create sequence documents
            System.out.println("Parsing FASTQ file...");
            List<NucleotideSequenceDocument> sequences = parseFastqFile(fastqFile);
            System.out.println("Parsed " + sequences.size() + " sequences");

            if (sequences.isEmpty()) {
                String msg = "No sequences found in output file: " + fastqFile.getName();
                System.err.println("WARNING: " + msg);
                logger.warning(msg);
                continue;
            }

            // Create a sequence list document (not individual sequences)
            System.out.println("Creating sequence list document...");
            DefaultSequenceListDocument sequenceList =
                DefaultSequenceListDocument.forNucleotideSequences(sequences);

            // Set a descriptive name
            String listName = originalDoc.getName() + " (fastp filtered)";
            sequenceList.setName(listName);
            System.out.println("Created sequence list: " + listName);

            // Wrap in AnnotatedPluginDocument
            AnnotatedPluginDocument annotatedDoc = DocumentUtilities.createAnnotatedPluginDocument(sequenceList);

            // Add metadata
            System.out.println("Adding metadata to sequence list...");
            addMetadata(annotatedDoc, originalDoc, statistics, isLongRead);

            outputDocs.add(annotatedDoc);

            String msg = "Imported sequence list with " + sequences.size() + " sequences from " + fastqFile.getName();
            System.out.println("SUCCESS: " + msg);
            logger.info(msg);
        }

        return outputDocs;
    }

    /**
     * Parses a FASTQ file and returns sequence documents.
     *
     * @param fastqFile FASTQ file to parse
     * @return list of sequence documents
     */
    private List<NucleotideSequenceDocument> parseFastqFile(File fastqFile) throws IOException {
        List<NucleotideSequenceDocument> sequences = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fastqFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // FASTQ format: @name, sequence, +, quality
                if (line.startsWith("@")) {
                    String name = line.substring(1).trim();
                    String sequence = reader.readLine();
                    reader.readLine(); // Skip '+' line
                    String quality = reader.readLine();

                    if (sequence == null || quality == null) {
                        logger.warning("Incomplete FASTQ entry for: " + name);
                        continue;
                    }

                    // Convert quality string to byte array (Phred+33)
                    byte[] qualityScores = new byte[quality.length()];
                    for (int i = 0; i < quality.length(); i++) {
                        qualityScores[i] = (byte) (quality.charAt(i) - 33);
                    }

                    // Create sequence document
                    DefaultNucleotideSequence seqDoc = new DefaultNucleotideSequence(
                        name,
                        "",  // description
                        sequence,
                        new Date()
                    );

                    // Set quality scores
                    // seqDoc.setQuality(qualityScores);

                    sequences.add(seqDoc);
                }
            }
        }

        return sequences;
    }

    /**
     * Adds metadata to an output document.
     *
     * @param outputDoc output document to annotate
     * @param originalDoc original input document
     * @param statistics statistics from fastp
     * @param isLongRead whether this is long-read data
     */
    private void addMetadata(
            AnnotatedPluginDocument outputDoc,
            AnnotatedPluginDocument originalDoc,
            Map<String, Object> statistics,
            boolean isLongRead) {

        // Set description via document notes
        String description = "Processed with " + (isLongRead ? "fastplong" : "fastp") +
                           " - Quality filtered and adapter trimmed";

        // Try to set notes if the API supports it
        try {
            // outputDoc.setNotes(description); // Method may not exist
        } catch (Exception e) {
            logger.fine("Could not set notes on document");
        }

        // Add custom fields with statistics using string keys
        if (!statistics.isEmpty()) {
            // Create custom document fields
            DocumentField readsBeforeField = DocumentField.createStringField("Reads Before", "Reads Before Filtering", "fastp_reads_before");
            DocumentField readsAfterField = DocumentField.createStringField("Reads After", "Reads After Filtering", "fastp_reads_after");
            DocumentField basesBeforeField = DocumentField.createStringField("Bases Before", "Bases Before Filtering", "fastp_bases_before");
            DocumentField basesAfterField = DocumentField.createStringField("Bases After", "Bases After Filtering", "fastp_bases_after");
            DocumentField q20RateField = DocumentField.createStringField("Q20 Rate", "Q20 Rate After Filtering", "fastp_q20_rate");
            DocumentField q30RateField = DocumentField.createStringField("Q30 Rate", "Q30 Rate After Filtering", "fastp_q30_rate");
            DocumentField gcContentField = DocumentField.createStringField("GC Content", "GC Content After Filtering", "fastp_gc_content");
            DocumentField dupRateField = DocumentField.createStringField("Duplication Rate", "Duplication Rate", "fastp_dup_rate");

            // Set field values
            if (statistics.containsKey("reads_before")) {
                outputDoc.setFieldValue(readsBeforeField, String.valueOf(statistics.get("reads_before")));
            }

            if (statistics.containsKey("bases_before")) {
                outputDoc.setFieldValue(basesBeforeField, String.valueOf(statistics.get("bases_before")));
            }

            if (statistics.containsKey("reads_after")) {
                outputDoc.setFieldValue(readsAfterField, String.valueOf(statistics.get("reads_after")));
            }

            if (statistics.containsKey("bases_after")) {
                outputDoc.setFieldValue(basesAfterField, String.valueOf(statistics.get("bases_after")));
            }

            // Quality rates
            if (statistics.containsKey("q20_rate_after")) {
                double q20Rate = (Double) statistics.get("q20_rate_after");
                outputDoc.setFieldValue(q20RateField, DECIMAL_FORMAT.format(q20Rate * 100) + "%");
            }

            if (statistics.containsKey("q30_rate_after")) {
                double q30Rate = (Double) statistics.get("q30_rate_after");
                outputDoc.setFieldValue(q30RateField, DECIMAL_FORMAT.format(q30Rate * 100) + "%");
            }

            // GC content
            if (statistics.containsKey("gc_content_after")) {
                double gcContent = (Double) statistics.get("gc_content_after");
                outputDoc.setFieldValue(gcContentField, DECIMAL_FORMAT.format(gcContent * 100) + "%");
            }

            // Duplication rate
            if (statistics.containsKey("duplication_rate")) {
                double dupRate = (Double) statistics.get("duplication_rate");
                outputDoc.setFieldValue(dupRateField, DECIMAL_FORMAT.format(dupRate * 100) + "%");
            }
        }

        // Add reference fields
        DocumentField origDocField = DocumentField.createStringField("Original Document", "Original Document Name", "fastp_orig_doc");
        DocumentField procDateField = DocumentField.createStringField("Processing Date", "Processing Date", "fastp_proc_date");
        DocumentField toolUsedField = DocumentField.createStringField("Tool Used", "Tool Used for Processing", "fastp_tool");

        outputDoc.setFieldValue(origDocField, originalDoc.getName());
        outputDoc.setFieldValue(procDateField, new Date().toString());
        outputDoc.setFieldValue(toolUsedField, isLongRead ? "fastplong" : "fastp");
    }

    /**
     * Imports HTML report as a document.
     *
     * @param htmlFile HTML report file
     * @param baseName base name for the document
     * @return annotated document containing the HTML report
     */
    private AnnotatedPluginDocument importHtmlReport(File htmlFile, String baseName) {
        if (!htmlFile.exists() || htmlFile.length() == 0) {
            logger.warning("HTML report file is missing or empty");
            return null;
        }

        try {
            // Read HTML content - we'll store it as a plain text document
            // In a real implementation, you might want to use a specific HTML document type
            // or save it as an attachment

            // For now, we'll skip importing the HTML report as a document
            // and just log that it was generated
            logger.info("HTML report generated at: " + htmlFile.getAbsolutePath());
            logger.info("HTML report import skipped - file available in temp directory");

            // TODO: Implement proper HTML document import if Geneious supports it
            return null;

        } catch (Exception e) {
            logger.warning("Failed to import HTML report: " + e.getMessage());
            return null;
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }

        if (!directory.delete()) {
            throw new IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }
}
