package com.biomatters.plugins.fastp;

import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import jebl.util.ProgressListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes fastp/fastplong commands and captures output.
 *
 * This class handles:
 * - Building command lines from parameters
 * - Executing external processes
 * - Capturing stdout/stderr
 * - Progress monitoring
 * - Error handling and reporting
 *
 * Command structure:
 * fastp [options] -i input.fastq -o output.fastq
 * fastp [options] -i in.R1.fastq -I in.R2.fastq -o out.R1.fastq -O out.R2.fastq
 * fastplong [options] -i input.fastq -o output.fastq
 *
 * Common options:
 * -q, --qualified_quality_phred: minimum quality value
 * -u, --unqualified_percent_limit: percent of bases allowed to be unqualified
 * -n, --n_base_limit: maximum N bases allowed
 * -l, --length_required: minimum length required
 * --length_limit: maximum length limit
 * -w, --thread: worker thread number
 * -j, --json: JSON report file
 * -h, --html: HTML report file
 * --detect_adapter_for_pe: enable adapter detection for paired-end
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpExecutor {

    private static final Logger logger = Logger.getLogger(FastpExecutor.class.getName());

    // Pattern to match progress information from fastp output
    // Example: "Read1 before filtering: ... after filtering: ..."
    // Example: "Finished in ... seconds"
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+)%");
    private static final Pattern READ_COUNT_PATTERN = Pattern.compile("(\\d+) reads passed");

    private Process process;
    private volatile boolean cancelled = false;
    private Thread outputReaderThread;
    private Thread errorReaderThread;

    /**
     * Executes fastp for single-end reads.
     *
     * @param binaryPath path to fastp/fastplong binary
     * @param inputFile input FASTQ file
     * @param outputFile output FASTQ file
     * @param jsonReport output JSON report file
     * @param htmlReport output HTML report file
     * @param options fastp options
     * @param progressListener progress listener for updates
     * @return FastpExecutionResult containing command, exit code, stdout, and stderr
     * @throws DocumentOperationException if execution fails
     */
    public FastpExecutionResult executeSingleEnd(
            String binaryPath,
            File inputFile,
            File outputFile,
            File jsonReport,
            File htmlReport,
            FastpOptions options,
            ProgressListener progressListener) throws DocumentOperationException {

        List<String> command = buildSingleEndCommand(
            binaryPath, inputFile, outputFile, jsonReport, htmlReport, options
        );

        return executeCommand(command, progressListener);
    }

    /**
     * Executes fastp for paired-end reads.
     *
     * @param binaryPath path to fastp binary
     * @param inputR1 input R1 FASTQ file
     * @param inputR2 input R2 FASTQ file
     * @param outputR1 output R1 FASTQ file
     * @param outputR2 output R2 FASTQ file
     * @param jsonReport output JSON report file
     * @param htmlReport output HTML report file
     * @param options fastp options
     * @param progressListener progress listener for updates
     * @return FastpExecutionResult containing command, exit code, stdout, and stderr
     * @throws DocumentOperationException if execution fails
     */
    public FastpExecutionResult executePairedEnd(
            String binaryPath,
            File inputR1,
            File inputR2,
            File outputR1,
            File outputR2,
            File jsonReport,
            File htmlReport,
            FastpOptions options,
            ProgressListener progressListener) throws DocumentOperationException {

        List<String> command = buildPairedEndCommand(
            binaryPath, inputR1, inputR2, outputR1, outputR2, jsonReport, htmlReport, options
        );

        return executeCommand(command, progressListener);
    }

    /**
     * Builds command line for single-end processing.
     *
     * @param binaryPath path to fastp binary
     * @param inputFile input FASTQ
     * @param outputFile output FASTQ
     * @param jsonReport JSON report file
     * @param htmlReport HTML report file
     * @param options fastp options
     * @return command as list of strings
     */
    private List<String> buildSingleEndCommand(
            String binaryPath,
            File inputFile,
            File outputFile,
            File jsonReport,
            File htmlReport,
            FastpOptions options) {

        List<String> command = new ArrayList<>();

        // Binary
        command.add(binaryPath);

        // Input/output files
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-o");
        command.add(outputFile.getAbsolutePath());

        // Report files
        command.add("-j");
        command.add(jsonReport.getAbsolutePath());
        command.add("-h");
        command.add(htmlReport.getAbsolutePath());

        // Add all other options
        addCommonOptions(command, options, false);

        logger.info("Built single-end command: " + String.join(" ", command));
        return command;
    }

    /**
     * Builds command line for paired-end processing.
     *
     * @param binaryPath path to fastp binary
     * @param inputR1 input R1 FASTQ
     * @param inputR2 input R2 FASTQ
     * @param outputR1 output R1 FASTQ
     * @param outputR2 output R2 FASTQ
     * @param jsonReport JSON report file
     * @param htmlReport HTML report file
     * @param options fastp options
     * @return command as list of strings
     */
    private List<String> buildPairedEndCommand(
            String binaryPath,
            File inputR1,
            File inputR2,
            File outputR1,
            File outputR2,
            File jsonReport,
            File htmlReport,
            FastpOptions options) {

        List<String> command = new ArrayList<>();

        // Binary
        command.add(binaryPath);

        // Input files
        command.add("-i");
        command.add(inputR1.getAbsolutePath());
        command.add("-I");
        command.add(inputR2.getAbsolutePath());

        // Output files
        command.add("-o");
        command.add(outputR1.getAbsolutePath());
        command.add("-O");
        command.add(outputR2.getAbsolutePath());

        // Report files
        command.add("-j");
        command.add(jsonReport.getAbsolutePath());
        command.add("-h");
        command.add(htmlReport.getAbsolutePath());

        // Add all other options
        addCommonOptions(command, options, true);

        logger.info("Built paired-end command: " + String.join(" ", command));
        return command;
    }

    /**
     * Adds common options to the command based on FastpOptions configuration.
     *
     * @param command the command list to add options to
     * @param options the FastpOptions configuration
     * @param isPairedEnd whether this is paired-end processing
     */
    private void addCommonOptions(List<String> command, FastpOptions options, boolean isPairedEnd) {
        // Threading
        command.add("-w");
        command.add(String.valueOf(options.getThreads()));

        // Compression level
        command.add("-z");
        command.add(String.valueOf(options.getCompressionLevel()));

        // Quality filtering options
        if (options.isQualityFilteringDisabled()) {
            command.add("-Q");
        } else {
            command.add("-q");
            command.add(String.valueOf(options.getQualifiedQualityPhred()));

            command.add("-u");
            command.add(String.valueOf(options.getUnqualifiedPercentLimit()));

            command.add("-n");
            command.add(String.valueOf(options.getNBaseLimit()));

            int avgQual = options.getAverageQuality();
            if (avgQual > 0) {
                command.add("-e");
                command.add(String.valueOf(avgQual));
            }
        }

        // Length filtering options
        if (options.isLengthFilteringDisabled()) {
            command.add("-L");
        } else {
            command.add("-l");
            command.add(String.valueOf(options.getLengthRequired()));

            int maxLength = options.getLengthLimit();
            if (maxLength > 0) {
                command.add("--length_limit");
                command.add(String.valueOf(maxLength));
            }
        }

        // Adapter trimming options
        if (options.isAdapterTrimmingDisabled()) {
            command.add("-A");
        } else {
            // Auto-detection
            if (options.isDetectAdapterEnabled()) {
                if (isPairedEnd) {
                    command.add("--detect_adapter_for_pe");
                }
            }

            // Custom adapters
            String adapterR1 = options.getAdapterR1();
            if (!adapterR1.isEmpty()) {
                command.add("--adapter_sequence");
                command.add(adapterR1);
            }

            if (isPairedEnd) {
                String adapterR2 = options.getAdapterR2();
                if (!adapterR2.isEmpty()) {
                    command.add("--adapter_sequence_r2");
                    command.add(adapterR2);
                }
            }
        }

        // Per-read quality cutting options
        if (options.isCutFrontEnabled()) {
            command.add("--cut_front");
        }
        if (options.isCutTailEnabled()) {
            command.add("--cut_tail");
        }
        if (options.isCutRightEnabled()) {
            command.add("--cut_right");
        }

        // Only add window size and quality if any cutting is enabled
        if (options.isCutFrontEnabled() || options.isCutTailEnabled() || options.isCutRightEnabled()) {
            command.add("--cut_window_size");
            command.add(String.valueOf(options.getCutWindowSize()));
            command.add("--cut_mean_quality");
            command.add(String.valueOf(options.getCutMeanQuality()));
        }

        // Base trimming options
        int trimFront1 = options.getTrimFront1();
        if (trimFront1 > 0) {
            command.add("-f");
            command.add(String.valueOf(trimFront1));
        }

        int trimTail1 = options.getTrimTail1();
        if (trimTail1 > 0) {
            command.add("-t");
            command.add(String.valueOf(trimTail1));
        }

        if (isPairedEnd) {
            int trimFront2 = options.getTrimFront2();
            if (trimFront2 > 0) {
                command.add("-F");
                command.add(String.valueOf(trimFront2));
            }

            int trimTail2 = options.getTrimTail2();
            if (trimTail2 > 0) {
                command.add("-T");
                command.add(String.valueOf(trimTail2));
            }
        }

        // PolyG trimming
        if (options.isTrimPolyGEnabled()) {
            command.add("-g");
            command.add("--poly_g_min_len");
            command.add(String.valueOf(options.getPolyGMinLen()));
        } else {
            command.add("-G");  // Disable polyG trimming
        }

        // PolyX trimming
        if (options.isTrimPolyXEnabled()) {
            command.add("-x");
            command.add("--poly_x_min_len");
            command.add(String.valueOf(options.getPolyXMinLen()));
        }

        // Paired-end specific options
        if (isPairedEnd) {
            if (options.isCorrectionEnabled()) {
                command.add("-c");
                command.add("--overlap_len_require");
                command.add(String.valueOf(options.getOverlapLenRequire()));
                command.add("--overlap_diff_limit");
                command.add(String.valueOf(options.getOverlapDiffLimit()));
            }
        }

        // UMI processing
        if (options.isUmiEnabled()) {
            command.add("-U");
            command.add("--umi_loc");
            command.add(options.getUmiLocation());
            command.add("--umi_len");
            command.add(String.valueOf(options.getUmiLength()));

            String umiPrefix = options.getUmiPrefix();
            if (!umiPrefix.isEmpty()) {
                command.add("--umi_prefix");
                command.add(umiPrefix);
            }

            int umiSkip = options.getUmiSkip();
            if (umiSkip > 0) {
                command.add("--umi_skip");
                command.add(String.valueOf(umiSkip));
            }
        }

        // Complexity filtering
        if (options.isComplexityFilterEnabled()) {
            command.add("-y");
            command.add("--complexity_threshold");
            command.add(String.valueOf(options.getComplexityThreshold()));
        }

        // Deduplication
        if (options.isDedupEnabled()) {
            command.add("-D");
        }
    }

    /**
     * Executes a command and captures output.
     *
     * This method:
     * - Builds the full command string for logging
     * - Starts the process
     * - Captures stdout/stderr in separate threads
     * - Updates progress listener based on output parsing
     * - Handles cancellation
     * - Waits for completion
     * - Returns FastpExecutionResult with command, exit code, stdout, and stderr
     *
     * @param command command to execute
     * @param progressListener progress listener
     * @return FastpExecutionResult containing execution details
     * @throws DocumentOperationException if execution fails
     */
    private FastpExecutionResult executeCommand(List<String> command, ProgressListener progressListener)
            throws DocumentOperationException {

        // Build the full command string for logging and result
        String commandString = String.join(" ", command);

        try {
            // Console logging of the command before execution
            System.out.println("=============================================================");
            System.out.println("Executing Fastp Command:");
            System.out.println("=============================================================");
            System.out.println(commandString);
            System.out.println("=============================================================");

            logger.info("Executing command: " + commandString);

            // Build and start process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);  // Keep stdout and stderr separate

            process = pb.start();
            logger.info("Process started");

            // Create output capture objects
            final StringBuilder stdoutCapture = new StringBuilder();
            final StringBuilder stderrCapture = new StringBuilder();

            // Read stdout in a separate thread
            outputReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutCapture.append(line).append("\n");
                        logger.fine("STDOUT: " + line);

                        // Check for cancellation
                        if (cancelled) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error reading stdout", e);
                }
            }, "FastpExecutor-StdOut");

            // Read stderr in a separate thread (fastp outputs progress here)
            errorReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrCapture.append(line).append("\n");
                        logger.fine("STDERR: " + line);

                        // Parse progress from stderr
                        parseProgress(line, progressListener);

                        // Check for cancellation
                        if (cancelled) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error reading stderr", e);
                }
            }, "FastpExecutor-StdErr");

            // Start reader threads
            outputReaderThread.start();
            errorReaderThread.start();

            // Wait for process to complete or cancellation
            int exitCode;
            while (true) {
                try {
                    if (cancelled) {
                        logger.info("Process cancelled by user");
                        process.destroy();

                        // Give it a moment to terminate gracefully
                        if (!process.waitFor(5, TimeUnit.SECONDS)) {
                            logger.warning("Process did not terminate gracefully, forcing...");
                            process.destroyForcibly();
                        }

                        throw new DocumentOperationException("Operation cancelled by user");
                    }

                    // Check if process has completed
                    if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                        exitCode = process.exitValue();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DocumentOperationException("Process interrupted", e);
                }
            }

            // Wait for reader threads to complete
            try {
                outputReaderThread.join(5000);
                errorReaderThread.join(5000);
            } catch (InterruptedException e) {
                logger.warning("Interrupted while waiting for reader threads");
                Thread.currentThread().interrupt();
            }

            logger.info("Process completed with exit code: " + exitCode);

            // Get captured output as strings
            String stdout = stdoutCapture.toString();
            String stderr = stderrCapture.toString();

            if (!stdout.isEmpty()) {
                logger.info("Process stdout:\n" + stdout);
            }
            if (!stderr.isEmpty()) {
                logger.info("Process stderr:\n" + stderr);
            }

            // Create the execution result
            FastpExecutionResult result = new FastpExecutionResult(commandString, exitCode, stdout, stderr);

            // Check for errors
            if (exitCode != 0) {
                String errorMessage = "Fastp execution failed with exit code: " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMessage += "\nError output:\n" + stderr;
                }
                throw new DocumentOperationException(errorMessage);
            }

            return result;

        } catch (IOException e) {
            throw new DocumentOperationException("Failed to execute fastp: " + e.getMessage(), e);
        } finally {
            // Clean up
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    /**
     * Parses progress information from fastp output line.
     *
     * Fastp outputs progress information to stderr in various formats.
     * This method attempts to extract progress percentage or read counts
     * and update the progress listener accordingly.
     *
     * @param line output line from fastp
     * @param progressListener progress listener to update
     */
    private void parseProgress(String line, ProgressListener progressListener) {
        if (progressListener == null || line == null) {
            return;
        }

        try {
            // Look for percentage patterns
            Matcher percentMatcher = PROGRESS_PATTERN.matcher(line);
            if (percentMatcher.find()) {
                int percent = Integer.parseInt(percentMatcher.group(1));
                progressListener.setProgress(percent / 100.0);
                return;
            }

            // Look for read count patterns
            Matcher readMatcher = READ_COUNT_PATTERN.matcher(line);
            if (readMatcher.find()) {
                // Just update the message for read counts
                progressListener.setMessage("Processing: " + line.trim());
                return;
            }

            // Look for specific fastp messages
            if (line.contains("Read1 before filtering") || line.contains("Read1 after filtering")) {
                progressListener.setMessage(line.trim());
            } else if (line.contains("Finished") || line.contains("completed")) {
                progressListener.setProgress(1.0);
                progressListener.setMessage("Fastp completed successfully");
            } else if (line.contains("started")) {
                progressListener.setProgress(0.0);
                progressListener.setMessage("Fastp started");
            }
        } catch (Exception e) {
            // Don't let progress parsing errors affect the main execution
            logger.log(Level.FINE, "Error parsing progress from: " + line, e);
        }
    }

    /**
     * Cancels the currently running process.
     */
    public void cancel() {
        logger.info("Cancel requested");
        cancelled = true;
        if (process != null && process.isAlive()) {
            logger.info("Destroying process");
            process.destroy();
        }
    }

    /**
     * Checks if the executor has been cancelled.
     *
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Resets the cancelled flag.
     */
    public void reset() {
        cancelled = false;
        process = null;
        outputReaderThread = null;
        errorReaderThread = null;
        logger.info("Executor reset");
    }
}
