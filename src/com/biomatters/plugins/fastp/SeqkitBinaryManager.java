package com.biomatters.plugins.fastp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages seqkit binary location and execution.
 *
 * This class handles:
 * - Extraction of bundled seqkit binary from JAR resources
 * - Platform-specific binary detection (currently macOS only)
 * - Binary validation and executable permissions
 * - Version checking
 * - Lifecycle management with cleanup on shutdown
 *
 * Binary detection strategy:
 * 1. Extract bundled binary from JAR resources to temp directory
 * 2. Make binary executable
 * 3. Cache binary location for subsequent use
 * 4. Clean up temporary binary on JVM shutdown
 *
 * Seqkit is a cross-platform toolkit for FASTA/Q file manipulation with
 * excellent performance. The binary should be a universal binary for macOS
 * (supporting both x86_64 and arm64 architectures).
 *
 * @author David Ho
 * @version 1.0.0
 */
public class SeqkitBinaryManager {

    private static final Logger logger = Logger.getLogger(SeqkitBinaryManager.class.getName());
    private static SeqkitBinaryManager instance;

    private static final String BINARY_RESOURCE_BASE = "/binaries/macos/seqkit/";
    private static final String SEQKIT_BINARY_NAME = "seqkit";

    private File seqkitBinary;
    private File tempDirectory;
    private boolean initialized = false;

    /**
     * Private constructor for singleton pattern.
     */
    private SeqkitBinaryManager() {
        // Initialize will be called separately
    }

    /**
     * Gets the singleton instance of the binary manager.
     *
     * @return the singleton instance
     */
    public static synchronized SeqkitBinaryManager getInstance() {
        if (instance == null) {
            instance = new SeqkitBinaryManager();
        }
        return instance;
    }

    /**
     * Initializes the binary manager by extracting and preparing bundled binary.
     *
     * This method:
     * 1. Detects the current operating system
     * 2. Creates a temporary directory for binary
     * 3. Extracts binary from JAR resources
     * 4. Sets executable permissions
     * 5. Validates binary works correctly
     * 6. Registers shutdown hook for cleanup
     *
     * @throws IOException if binary cannot be extracted or is not executable
     */
    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        logger.info("Initializing SeqkitBinaryManager...");

        // Detect OS and verify macOS
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac")) {
            throw new IOException(
                "Unsupported operating system: " + osName + ". " +
                "This plugin currently only supports macOS."
            );
        }

        // Create temporary directory for binary
        tempDirectory = createTempDirectory();
        logger.info("Created temp directory for seqkit: " + tempDirectory.getAbsolutePath());

        // Extract and setup binary
        try {
            seqkitBinary = extractBinary(SEQKIT_BINARY_NAME);

            // Validate binary
            if (!validateBinary(seqkitBinary)) {
                throw new IOException("Seqkit binary validation failed");
            }

            // Test binary with version command
            testBinaryVersion(seqkitBinary, "seqkit");

            // Register shutdown hook to clean up temp files
            registerShutdownHook();

            initialized = true;
            logger.info("SeqkitBinaryManager initialized successfully");

        } catch (IOException e) {
            // Clean up on failure
            cleanup();
            throw e;
        }
    }

    /**
     * Creates a temporary directory for storing extracted binary.
     *
     * @return the temporary directory
     * @throws IOException if directory cannot be created
     */
    private File createTempDirectory() throws IOException {
        Path tempPath = Files.createTempDirectory("seqkit-plugin-");
        File tempDir = tempPath.toFile();
        tempDir.deleteOnExit(); // Fallback cleanup
        return tempDir;
    }

    /**
     * Extracts a binary from JAR resources to the temp directory.
     *
     * @param binaryName name of the binary to extract
     * @return File object pointing to the extracted binary
     * @throws IOException if extraction fails
     */
    private File extractBinary(String binaryName) throws IOException {
        String resourcePath = BINARY_RESOURCE_BASE + binaryName;
        logger.info("Extracting binary from resource: " + resourcePath);

        // Get input stream from JAR resources
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Binary resource not found: " + resourcePath);
        }

        try {
            // Create output file in temp directory
            File outputFile = new File(tempDirectory, binaryName);
            outputFile.deleteOnExit(); // Fallback cleanup

            // Copy binary from JAR to temp file
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Make binary executable
            if (!outputFile.setExecutable(true, false)) {
                throw new IOException("Failed to set executable permission on: " + outputFile.getAbsolutePath());
            }

            logger.info("Successfully extracted and made executable: " + outputFile.getAbsolutePath());
            return outputFile;

        } finally {
            inputStream.close();
        }
    }

    /**
     * Validates that a binary is executable and functional.
     *
     * @param binary the binary file to validate
     * @return true if valid and executable
     */
    private boolean validateBinary(File binary) {
        if (binary == null) {
            logger.warning("Binary is null");
            return false;
        }

        if (!binary.exists()) {
            logger.warning("Binary does not exist: " + binary.getAbsolutePath());
            return false;
        }

        if (!binary.isFile()) {
            logger.warning("Binary is not a file: " + binary.getAbsolutePath());
            return false;
        }

        if (!binary.canExecute()) {
            logger.warning("Binary is not executable: " + binary.getAbsolutePath());
            return false;
        }

        logger.info("Binary validation passed: " + binary.getAbsolutePath());
        return true;
    }

    /**
     * Tests a binary by running it with version command.
     * Seqkit uses 'seqkit version' command.
     *
     * @param binary the binary to test
     * @param name the name of the binary (for logging)
     * @throws IOException if the binary fails to execute
     */
    private void testBinaryVersion(File binary, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(name + " binary test failed with exit code: " + exitCode);
            }

            String version = output.toString().trim();
            logger.info(name + " version check successful: " + version);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Binary version check interrupted for: " + name, e);
        }
    }

    /**
     * Registers a shutdown hook to clean up temporary binary.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                cleanup();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during shutdown cleanup", e);
            }
        }, "SeqkitBinaryManager-Cleanup"));
        logger.info("Registered shutdown hook for cleanup");
    }

    /**
     * Cleans up temporary binary file and directory.
     */
    private void cleanup() {
        logger.info("Cleaning up temporary seqkit binary...");

        if (seqkitBinary != null && seqkitBinary.exists()) {
            if (seqkitBinary.delete()) {
                logger.fine("Deleted seqkit binary: " + seqkitBinary.getAbsolutePath());
            }
        }

        if (tempDirectory != null && tempDirectory.exists()) {
            if (tempDirectory.delete()) {
                logger.fine("Deleted temp directory: " + tempDirectory.getAbsolutePath());
            }
        }

        logger.info("Cleanup completed");
    }

    /**
     * Gets the File object for the seqkit binary.
     *
     * @return seqkit binary File
     * @throws IOException if not initialized or binary not available
     */
    public File getSeqkitBinary() throws IOException {
        if (!initialized) {
            initialize();
        }
        if (seqkitBinary == null || !seqkitBinary.exists()) {
            throw new IOException("Seqkit binary not available");
        }
        return seqkitBinary;
    }

    /**
     * Gets the absolute path to the seqkit binary.
     *
     * @return seqkit binary path
     * @throws IOException if not initialized or binary not found
     */
    public String getSeqkitPath() throws IOException {
        return getSeqkitBinary().getAbsolutePath();
    }

    /**
     * Checks if seqkit binary is available.
     *
     * @return true if seqkit is available
     */
    public boolean isSeqkitAvailable() {
        try {
            getSeqkitBinary();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the version of seqkit by executing it with version command.
     *
     * @return version string
     * @throws IOException if version cannot be determined
     */
    public String getSeqkitVersion() throws IOException {
        return getBinaryVersion(getSeqkitBinary(), "seqkit");
    }

    /**
     * Gets the version string from seqkit by executing it with version command.
     *
     * @param binary the binary to query
     * @param name the name of the binary (for error messages)
     * @return version string
     * @throws IOException if version cannot be determined
     */
    private String getBinaryVersion(File binary, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to get " + name + " version. Exit code: " + exitCode);
            }

            String version = output.toString().trim();
            if (version.isEmpty()) {
                throw new IOException("Empty version output from " + name);
            }

            return version;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Version check interrupted for: " + name, e);
        }
    }

    /**
     * Executes a seqkit command with the given arguments.
     * This is a helper method for running seqkit commands.
     *
     * @param args command line arguments for seqkit
     * @return Process object for the running seqkit command
     * @throws IOException if seqkit cannot be executed
     */
    public ProcessBuilder createSeqkitProcess(String... args) throws IOException {
        File binary = getSeqkitBinary();
        String[] commandArray = new String[args.length + 1];
        commandArray[0] = binary.getAbsolutePath();
        System.arraycopy(args, 0, commandArray, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(commandArray);
        pb.redirectErrorStream(true);
        return pb;
    }

    /**
     * Resets the binary manager, forcing re-initialization on next use.
     * This also cleans up any existing temporary files.
     */
    public void reset() {
        cleanup();
        seqkitBinary = null;
        tempDirectory = null;
        initialized = false;
        logger.info("Seqkit binary manager reset");
    }

    /**
     * Gets information about the current binary setup.
     *
     * @return String with binary location and version information
     */
    public String getBinaryInfo() {
        StringBuilder info = new StringBuilder();
        info.append("SeqkitBinaryManager Status:\n");
        info.append("Initialized: ").append(initialized).append("\n");

        if (initialized) {
            info.append("Temp Directory: ").append(tempDirectory != null ? tempDirectory.getAbsolutePath() : "null").append("\n");

            try {
                info.append("Seqkit: ").append(getSeqkitPath()).append("\n");
                info.append("Seqkit Version: ").append(getSeqkitVersion()).append("\n");
            } catch (IOException e) {
                info.append("Seqkit: Not available - ").append(e.getMessage()).append("\n");
            }
        }

        return info.toString();
    }
}
