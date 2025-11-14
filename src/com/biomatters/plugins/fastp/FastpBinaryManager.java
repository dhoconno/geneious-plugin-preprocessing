package com.biomatters.plugins.fastp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages fastp and fastplong binary locations and execution.
 *
 * This class handles:
 * - Extraction of bundled binaries from JAR resources
 * - Platform-specific binary detection (currently macOS only)
 * - Binary validation and executable permissions
 * - Version checking
 * - Lifecycle management with cleanup on shutdown
 *
 * Binary detection strategy:
 * 1. Extract bundled binaries from JAR resources to temp directory
 * 2. Make binaries executable
 * 3. Cache binary locations for subsequent use
 * 4. Clean up temporary binaries on JVM shutdown
 *
 * The binaries are universal binaries (x86_64 + arm64) for macOS and are
 * statically linked, requiring no external dependencies except system libraries.
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpBinaryManager {

    private static final Logger logger = Logger.getLogger(FastpBinaryManager.class.getName());
    private static FastpBinaryManager instance;

    private static final String BINARY_RESOURCE_BASE = "/binaries/macos/";
    private static final String FASTP_BINARY_NAME = "fastp";
    private static final String FASTPLONG_BINARY_NAME = "fastplong";

    private File fastpBinary;
    private File fastplongBinary;
    private File tempDirectory;
    private boolean initialized = false;

    /**
     * Private constructor for singleton pattern.
     */
    private FastpBinaryManager() {
        // Initialize will be called separately
    }

    /**
     * Gets the singleton instance of the binary manager.
     *
     * @return the singleton instance
     */
    public static synchronized FastpBinaryManager getInstance() {
        if (instance == null) {
            instance = new FastpBinaryManager();
        }
        return instance;
    }

    /**
     * Initializes the binary manager by extracting and preparing bundled binaries.
     *
     * This method:
     * 1. Detects the current operating system
     * 2. Creates a temporary directory for binaries
     * 3. Extracts binaries from JAR resources
     * 4. Sets executable permissions
     * 5. Validates binaries work correctly
     * 6. Registers shutdown hook for cleanup
     *
     * @throws IOException if binaries cannot be extracted or are not executable
     */
    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        logger.info("Initializing FastpBinaryManager...");

        // Detect OS and verify macOS
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac")) {
            throw new IOException(
                "Unsupported operating system: " + osName + ". " +
                "This plugin currently only supports macOS."
            );
        }

        // Create temporary directory for binaries
        tempDirectory = createTempDirectory();
        logger.info("Created temp directory for binaries: " + tempDirectory.getAbsolutePath());

        // Extract and setup binaries
        try {
            fastpBinary = extractBinary(FASTP_BINARY_NAME);
            fastplongBinary = extractBinary(FASTPLONG_BINARY_NAME);

            // Validate binaries
            if (!validateBinary(fastpBinary)) {
                throw new IOException("Fastp binary validation failed");
            }
            if (!validateBinary(fastplongBinary)) {
                throw new IOException("Fastplong binary validation failed");
            }

            // Test binaries with --version
            testBinaryVersion(fastpBinary, "fastp");
            testBinaryVersion(fastplongBinary, "fastplong");

            // Register shutdown hook to clean up temp files
            registerShutdownHook();

            initialized = true;
            logger.info("FastpBinaryManager initialized successfully");

        } catch (IOException e) {
            // Clean up on failure
            cleanup();
            throw e;
        }
    }

    /**
     * Creates a temporary directory for storing extracted binaries.
     *
     * @return the temporary directory
     * @throws IOException if directory cannot be created
     */
    private File createTempDirectory() throws IOException {
        Path tempPath = Files.createTempDirectory("fastp-plugin-");
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
     * Tests a binary by running it with --version flag.
     *
     * @param binary the binary to test
     * @param name the name of the binary (for logging)
     * @throws IOException if the binary fails to execute
     */
    private void testBinaryVersion(File binary, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "--version");
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
     * Registers a shutdown hook to clean up temporary binaries.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                cleanup();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during shutdown cleanup", e);
            }
        }, "FastpBinaryManager-Cleanup"));
        logger.info("Registered shutdown hook for cleanup");
    }

    /**
     * Cleans up temporary binary files and directory.
     */
    private void cleanup() {
        logger.info("Cleaning up temporary binaries...");

        if (fastpBinary != null && fastpBinary.exists()) {
            if (fastpBinary.delete()) {
                logger.fine("Deleted fastp binary: " + fastpBinary.getAbsolutePath());
            }
        }

        if (fastplongBinary != null && fastplongBinary.exists()) {
            if (fastplongBinary.delete()) {
                logger.fine("Deleted fastplong binary: " + fastplongBinary.getAbsolutePath());
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
     * Gets the File object for the fastp binary.
     *
     * @return fastp binary File
     * @throws IOException if not initialized or binary not available
     */
    public File getFastpBinary() throws IOException {
        if (!initialized) {
            initialize();
        }
        if (fastpBinary == null || !fastpBinary.exists()) {
            throw new IOException("Fastp binary not available");
        }
        return fastpBinary;
    }

    /**
     * Gets the File object for the fastplong binary.
     *
     * @return fastplong binary File
     * @throws IOException if not initialized or binary not available
     */
    public File getFastplongBinary() throws IOException {
        if (!initialized) {
            initialize();
        }
        if (fastplongBinary == null || !fastplongBinary.exists()) {
            throw new IOException("Fastplong binary not available");
        }
        return fastplongBinary;
    }

    /**
     * Gets the absolute path to the fastp binary.
     *
     * @return fastp binary path
     * @throws IOException if not initialized or binary not found
     */
    public String getFastpPath() throws IOException {
        return getFastpBinary().getAbsolutePath();
    }

    /**
     * Gets the absolute path to the fastplong binary.
     *
     * @return fastplong binary path
     * @throws IOException if not initialized or binary not found
     */
    public String getFastplongPath() throws IOException {
        return getFastplongBinary().getAbsolutePath();
    }

    /**
     * Checks if fastp binary is available.
     *
     * @return true if fastp is available
     */
    public boolean isFastpAvailable() {
        try {
            getFastpBinary();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if fastplong binary is available.
     *
     * @return true if fastplong is available
     */
    public boolean isFastplongAvailable() {
        try {
            getFastplongBinary();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the version of fastp by executing it with --version flag.
     *
     * @return version string
     * @throws IOException if version cannot be determined
     */
    public String getFastpVersion() throws IOException {
        return getBinaryVersion(getFastpBinary(), "fastp");
    }

    /**
     * Gets the version of fastplong by executing it with --version flag.
     *
     * @return version string
     * @throws IOException if version cannot be determined
     */
    public String getFastplongVersion() throws IOException {
        return getBinaryVersion(getFastplongBinary(), "fastplong");
    }

    /**
     * Gets the version string from a binary by executing it with --version.
     *
     * @param binary the binary to query
     * @param name the name of the binary (for error messages)
     * @return version string
     * @throws IOException if version cannot be determined
     */
    private String getBinaryVersion(File binary, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "--version");
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
     * Resets the binary manager, forcing re-initialization on next use.
     * This also cleans up any existing temporary files.
     */
    public void reset() {
        cleanup();
        fastpBinary = null;
        fastplongBinary = null;
        tempDirectory = null;
        initialized = false;
        logger.info("Binary manager reset");
    }

    /**
     * Gets information about the current binary setup.
     *
     * @return String with binary location and version information
     */
    public String getBinaryInfo() {
        StringBuilder info = new StringBuilder();
        info.append("FastpBinaryManager Status:\n");
        info.append("Initialized: ").append(initialized).append("\n");

        if (initialized) {
            info.append("Temp Directory: ").append(tempDirectory != null ? tempDirectory.getAbsolutePath() : "null").append("\n");

            try {
                info.append("Fastp: ").append(getFastpPath()).append("\n");
                info.append("Fastp Version: ").append(getFastpVersion()).append("\n");
            } catch (IOException e) {
                info.append("Fastp: Not available - ").append(e.getMessage()).append("\n");
            }

            try {
                info.append("Fastplong: ").append(getFastplongPath()).append("\n");
                info.append("Fastplong Version: ").append(getFastplongVersion()).append("\n");
            } catch (IOException e) {
                info.append("Fastplong: Not available - ").append(e.getMessage()).append("\n");
            }
        }

        return info.toString();
    }
}
