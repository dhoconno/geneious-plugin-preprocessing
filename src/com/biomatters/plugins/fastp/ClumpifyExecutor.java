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

/**
 * Executes clumpify.sh from BBTools to optimize FASTQ files for compression.
 *
 * Clumpify reorders reads by sequence similarity, which provides better
 * compression ratios (2-4x) and improved performance for downstream tools.
 *
 * Command structure:
 * clumpify.sh in=input.fastq out=output.fastq reorder=t
 * clumpify.sh in1=R1.fastq in2=R2.fastq out1=R1.clumped.fastq out2=R2.clumped.fastq reorder=t
 *
 * Reference: https://www.biostars.org/p/225338/
 *
 * @author David Ho
 * @version 1.1.0
 */
public class ClumpifyExecutor {

    private static final Logger logger = Logger.getLogger(ClumpifyExecutor.class.getName());

    private Process process;
    private volatile boolean cancelled = false;
    private Thread outputReaderThread;
    private Thread errorReaderThread;

    /**
     * Executes clumpify for single-end reads.
     *
     * @param clumpifyPath path to clumpify.sh script
     * @param inputFile input FASTQ file
     * @param outputFile output clumpified FASTQ file
     * @param additionalArgs additional command line arguments
     * @param progressListener progress listener for updates
     * @return FastpExecutionResult containing command, exit code, stdout, and stderr
     * @throws DocumentOperationException if execution fails
     */
    public FastpExecutionResult executeSingleEnd(
            String clumpifyPath,
            File inputFile,
            File outputFile,
            List<String> additionalArgs,
            ProgressListener progressListener) throws DocumentOperationException {

        List<String> command = buildSingleEndCommand(clumpifyPath, inputFile, outputFile, additionalArgs);
        return executeCommand(command, progressListener);
    }

    /**
     * Executes clumpify for paired-end reads.
     *
     * @param clumpifyPath path to clumpify.sh script
     * @param inputR1 input R1 FASTQ file
     * @param inputR2 input R2 FASTQ file
     * @param outputR1 output clumpified R1 FASTQ file
     * @param outputR2 output clumpified R2 FASTQ file
     * @param additionalArgs additional command line arguments
     * @param progressListener progress listener for updates
     * @return FastpExecutionResult containing command, exit code, stdout, and stderr
     * @throws DocumentOperationException if execution fails
     */
    public FastpExecutionResult executePairedEnd(
            String clumpifyPath,
            File inputR1,
            File inputR2,
            File outputR1,
            File outputR2,
            List<String> additionalArgs,
            ProgressListener progressListener) throws DocumentOperationException {

        List<String> command = buildPairedEndCommand(clumpifyPath, inputR1, inputR2, outputR1, outputR2, additionalArgs);
        return executeCommand(command, progressListener);
    }

    /**
     * Builds command line for single-end clumpify processing.
     *
     * @param clumpifyPath path to clumpify.sh
     * @param inputFile input FASTQ
     * @param outputFile output FASTQ
     * @param additionalArgs additional command line arguments
     * @return command as list of strings
     */
    private List<String> buildSingleEndCommand(String clumpifyPath, File inputFile, File outputFile,
                                               List<String> additionalArgs) {
        List<String> command = new ArrayList<>();

        command.add(clumpifyPath);
        command.add("in=" + inputFile.getAbsolutePath());
        command.add("out=" + outputFile.getAbsolutePath());

        // Add additional arguments from options
        if (additionalArgs != null && !additionalArgs.isEmpty()) {
            command.addAll(additionalArgs);
        }

        logger.info("Built single-end clumpify command: " + String.join(" ", command));
        return command;
    }

    /**
     * Builds command line for paired-end clumpify processing.
     *
     * @param clumpifyPath path to clumpify.sh
     * @param inputR1 input R1 FASTQ
     * @param inputR2 input R2 FASTQ
     * @param outputR1 output R1 FASTQ
     * @param outputR2 output R2 FASTQ
     * @param additionalArgs additional command line arguments
     * @return command as list of strings
     */
    private List<String> buildPairedEndCommand(
            String clumpifyPath,
            File inputR1,
            File inputR2,
            File outputR1,
            File outputR2,
            List<String> additionalArgs) {

        List<String> command = new ArrayList<>();

        command.add(clumpifyPath);
        command.add("in1=" + inputR1.getAbsolutePath());
        command.add("in2=" + inputR2.getAbsolutePath());
        command.add("out1=" + outputR1.getAbsolutePath());
        command.add("out2=" + outputR2.getAbsolutePath());

        // Add additional arguments from options
        if (additionalArgs != null && !additionalArgs.isEmpty()) {
            command.addAll(additionalArgs);
        }

        logger.info("Built paired-end clumpify command: " + String.join(" ", command));
        return command;
    }

    /**
     * Executes a command and captures output.
     *
     * @param command command to execute
     * @param progressListener progress listener
     * @return FastpExecutionResult containing execution details
     * @throws DocumentOperationException if execution fails
     */
    private FastpExecutionResult executeCommand(List<String> command, ProgressListener progressListener)
            throws DocumentOperationException {

        String commandString = String.join(" ", command);

        try {
            System.out.println("=============================================================");
            System.out.println("Executing Clumpify Command:");
            System.out.println("=============================================================");
            System.out.println(commandString);
            System.out.println("=============================================================");

            logger.info("Executing command: " + commandString);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);

            process = pb.start();
            logger.info("Clumpify process started");

            final StringBuilder stdoutCapture = new StringBuilder();
            final StringBuilder stderrCapture = new StringBuilder();

            // Read stdout
            outputReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutCapture.append(line).append("\n");
                        logger.fine("STDOUT: " + line);

                        if (cancelled) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error reading stdout", e);
                }
            }, "ClumpifyExecutor-StdOut");

            // Read stderr (clumpify outputs progress here)
            errorReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrCapture.append(line).append("\n");
                        logger.fine("STDERR: " + line);

                        // Update progress message with clumpify output
                        if (progressListener != null && !line.trim().isEmpty()) {
                            progressListener.setMessage("Clumpify: " + line.trim());
                        }

                        if (cancelled) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error reading stderr", e);
                }
            }, "ClumpifyExecutor-StdErr");

            outputReaderThread.start();
            errorReaderThread.start();

            // Wait for process completion
            int exitCode;
            while (true) {
                try {
                    if (cancelled) {
                        logger.info("Clumpify process cancelled by user");
                        process.destroy();

                        if (!process.waitFor(5, TimeUnit.SECONDS)) {
                            logger.warning("Clumpify process did not terminate gracefully, forcing...");
                            process.destroyForcibly();
                        }

                        throw new DocumentOperationException("Clumpify operation cancelled by user");
                    }

                    if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                        exitCode = process.exitValue();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DocumentOperationException("Clumpify process interrupted", e);
                }
            }

            // Wait for reader threads
            try {
                outputReaderThread.join(5000);
                errorReaderThread.join(5000);
            } catch (InterruptedException e) {
                logger.warning("Interrupted while waiting for reader threads");
                Thread.currentThread().interrupt();
            }

            logger.info("Clumpify process completed with exit code: " + exitCode);

            String stdout = stdoutCapture.toString();
            String stderr = stderrCapture.toString();

            if (!stdout.isEmpty()) {
                logger.info("Clumpify stdout:\n" + stdout);
            }
            if (!stderr.isEmpty()) {
                logger.info("Clumpify stderr:\n" + stderr);
            }

            FastpExecutionResult result = new FastpExecutionResult(commandString, exitCode, stdout, stderr);

            if (exitCode != 0) {
                String errorMessage = "Clumpify execution failed with exit code: " + exitCode;
                if (!stderr.isEmpty()) {
                    errorMessage += "\nError output:\n" + stderr;
                }
                throw new DocumentOperationException(errorMessage);
            }

            return result;

        } catch (IOException e) {
            throw new DocumentOperationException("Failed to execute clumpify: " + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    /**
     * Cancels the currently running clumpify process.
     */
    public void cancel() {
        logger.info("Clumpify cancel requested");
        cancelled = true;
        if (process != null && process.isAlive()) {
            logger.info("Destroying clumpify process");
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
        logger.info("Clumpify executor reset");
    }
}
