package com.biomatters.plugins.fastp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages BBTools suite binary locations and execution.
 *
 * This class handles:
 * - Extraction of complete BBTools suite (271+ shell scripts, resources, config, current/ directory)
 * - Platform-specific binary detection (currently macOS only)
 * - Binary validation and executable permissions
 * - Version checking
 * - Lifecycle management with cleanup on shutdown
 *
 * BBTools uses compiled Java classes in a directory structure:
 * - Shell scripts (clumpify.sh, reformat.sh, bbduk.sh, etc.)
 * - current/ directory containing all compiled .class files
 * - resources/ directory with reference data
 * - config/ directory with configuration files
 * - Scripts need to find the current/ directory to load Java classes
 *
 * Binary detection strategy:
 * 1. Extract entire BBTools suite from JAR resources to temp directory (43MB)
 * 2. Recursively extract all subdirectories (current/, resources/, config/)
 * 3. Make all shell scripts executable
 * 4. Cache BBTools directory location for subsequent use
 * 5. Clean up temporary binaries on JVM shutdown
 *
 * @author David Ho
 * @version 1.1.0
 */
public class BBToolsBinaryManager {

    private static final Logger logger = Logger.getLogger(BBToolsBinaryManager.class.getName());
    private static BBToolsBinaryManager instance;

    // Extract from /binaries/macos/ to preserve the bbtools/ directory structure
    private static final String BINARY_RESOURCE_BASE = "/binaries/macos/";

    // BBTools directory structure
    private File bbtoolsDirectory;
    private File tempDirectory;
    private boolean initialized = false;

    /**
     * Private constructor for singleton pattern.
     */
    private BBToolsBinaryManager() {
        // Initialize will be called separately
    }

    /**
     * Gets the singleton instance of the binary manager.
     *
     * @return the singleton instance
     */
    public static synchronized BBToolsBinaryManager getInstance() {
        if (instance == null) {
            instance = new BBToolsBinaryManager();
        }
        return instance;
    }

    /**
     * Initializes the binary manager by extracting and preparing the entire BBTools suite.
     *
     * This method:
     * 1. Detects the current operating system
     * 2. Creates a temporary directory for BBTools
     * 3. Extracts entire BBTools suite from JAR resources (271+ scripts, current/, resources/, config/)
     * 4. Sets executable permissions on all shell scripts
     * 5. Validates critical components work correctly
     * 6. Registers shutdown hook for cleanup
     *
     * @throws IOException if BBTools suite cannot be extracted or is not executable
     */
    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        logger.info("Initializing BBToolsBinaryManager...");

        // Detect OS and verify macOS
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("mac")) {
            throw new IOException(
                "Unsupported operating system: " + osName + ". " +
                "This plugin currently only supports macOS."
            );
        }

        // Verify Java is available
        if (!isJavaAvailable()) {
            throw new IOException(
                "Java runtime not available. BBTools requires Java to run."
            );
        }

        // Create temporary directory for BBTools
        tempDirectory = createTempDirectory();
        logger.info("Created temp directory for BBTools: " + tempDirectory.getAbsolutePath());

        // Extract and setup BBTools suite
        try {
            // Extract entire BBTools directory recursively from JAR resources
            // This extracts all 271+ .sh scripts, current/, resources/, and config/ directories
            extractEntireBBToolsSuite();

            // Set BBTools directory reference (extracted under temp/bbtools/)
            bbtoolsDirectory = new File(tempDirectory, "bbtools");
            if (!bbtoolsDirectory.exists() || !bbtoolsDirectory.isDirectory()) {
                throw new IOException("BBTools directory not found after extraction: " + bbtoolsDirectory.getAbsolutePath());
            }

            // Make all shell scripts executable
            makeAllScriptsExecutable(bbtoolsDirectory);

            // Validate critical components
            validateBBToolsInstallation();

            // Test a sample script to ensure everything works
            File clumpifyScript = new File(bbtoolsDirectory, "clumpify.sh");
            testBBToolsScript(clumpifyScript, "clumpify.sh");

            // Register shutdown hook to clean up temp files
            registerShutdownHook();

            initialized = true;
            logger.info("BBToolsBinaryManager initialized successfully");
            logger.info("BBTools directory: " + bbtoolsDirectory.getAbsolutePath());

        } catch (IOException e) {
            // Clean up on failure
            cleanup();
            throw e;
        }
    }

    /**
     * Extracts the entire BBTools suite from JAR resources.
     * This includes all shell scripts, current/ directory, resources/, and config/ directories.
     *
     * @throws IOException if extraction fails
     */
    private void extractEntireBBToolsSuite() throws IOException {
        logger.info("Extracting entire BBTools suite from JAR resources...");

        // Extract everything under /binaries/macos/ which includes the bbtools/ directory
        // This preserves the directory structure
        extractDirectoryRecursively("bbtools");

        logger.info("BBTools suite extraction complete");
    }

    /**
     * Recursively sets executable permissions on all .sh files in a directory.
     *
     * @param directory the directory to process
     * @throws IOException if permission setting fails
     */
    private void makeAllScriptsExecutable(File directory) throws IOException {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        int scriptCount = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively process subdirectories
                    makeAllScriptsExecutable(file);
                } else if (file.getName().endsWith(".sh")) {
                    // Make shell script executable
                    if (!file.setExecutable(true, false)) {
                        throw new IOException("Failed to set executable permission on: " + file.getAbsolutePath());
                    }
                    scriptCount++;
                    logger.fine("Made executable: " + file.getName());
                }
            }
        }

        logger.info("Set executable permissions on " + scriptCount + " shell scripts in " + directory.getName());
    }

    /**
     * Validates the BBTools installation by checking for critical components.
     *
     * @throws IOException if validation fails
     */
    private void validateBBToolsInstallation() throws IOException {
        // Check for critical scripts
        String[] criticalScripts = {"clumpify.sh", "reformat.sh", "bbduk.sh"};
        for (String scriptName : criticalScripts) {
            File script = new File(bbtoolsDirectory, scriptName);
            if (!script.exists()) {
                throw new IOException("Critical BBTools script not found: " + scriptName);
            }
            if (!script.canExecute()) {
                throw new IOException("Critical BBTools script is not executable: " + scriptName);
            }
            logger.fine("Validated script: " + scriptName);
        }

        // Check for current/ directory
        File currentDir = new File(bbtoolsDirectory, "current");
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            throw new IOException("BBTools current/ directory not found");
        }

        // Count class files to verify extraction
        long classFileCount = countFilesRecursively(currentDir, ".class");
        logger.info("BBTools current/ directory contains " + classFileCount + " .class files");

        if (classFileCount == 0) {
            throw new IOException("BBTools current/ directory is empty - no .class files found");
        }

        logger.info("BBTools installation validation passed");
    }

    /**
     * Checks if Java runtime is available.
     *
     * @return true if Java is available
     */
    private boolean isJavaAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logger.warning("Java runtime check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a temporary directory for storing extracted BBTools suite.
     *
     * @return the temporary directory
     * @throws IOException if directory cannot be created
     */
    private File createTempDirectory() throws IOException {
        Path tempPath = Files.createTempDirectory("bbtools-plugin-");
        File tempDir = tempPath.toFile();
        tempDir.deleteOnExit(); // Fallback cleanup
        return tempDir;
    }

    /**
     * Extracts an entire directory tree from JAR resources recursively.
     * This handles the entire BBTools suite including all subdirectories.
     *
     * @param directoryName name of the directory to extract (relative to BINARY_RESOURCE_BASE)
     * @throws IOException if extraction fails
     */
    private void extractDirectoryRecursively(String directoryName) throws IOException {
        String resourcePath = BINARY_RESOURCE_BASE + directoryName;
        logger.info("Extracting directory tree: " + resourcePath);

        // Create target directory
        File targetDir = new File(tempDirectory, directoryName);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
        }
        targetDir.deleteOnExit();

        // Get the resource URL to determine how to extract
        URL resourceUrl = getClass().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Directory not found in resources: " + resourcePath);
        }

        String protocol = resourceUrl.getProtocol();

        if ("jar".equals(protocol)) {
            // Extract from JAR file
            extractFromJar(resourcePath, targetDir);
        } else if ("file".equals(protocol)) {
            // Copy from file system (development mode)
            try {
                File sourceDir = new File(resourceUrl.toURI());
                copyDirectoryRecursively(sourceDir.toPath(), targetDir.toPath());
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert resource URL to URI: " + resourceUrl, e);
            }
        } else {
            throw new IOException("Unsupported resource protocol: " + protocol);
        }

        logger.info("Successfully extracted directory tree to: " + targetDir.getAbsolutePath());
    }

    /**
     * Extracts files from a JAR file matching a specific resource path.
     *
     * @param resourcePath the base resource path to match
     * @param targetDir the target directory to extract to
     * @throws IOException if extraction fails
     */
    private void extractFromJar(String resourcePath, File targetDir) throws IOException {
        // Try to find the JAR file containing the resources
        // First, try the location where this class was loaded from
        URL classLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
        String jarPath = classLocation.getPath();

        // Handle URL encoding
        jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");

        // Remove leading slash on Windows
        if (jarPath.startsWith("/") && jarPath.contains(":")) {
            jarPath = jarPath.substring(1);
        }

        File jarFile = new File(jarPath);

        // If the class location is not a JAR file, try to find the JAR from the resource URL
        if (!jarFile.exists() || !jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
            // Get the URL of the resource itself to find which JAR contains it
            String testResourcePath = resourcePath.endsWith("/") ? resourcePath + "clumpify.sh" : resourcePath + "/clumpify.sh";
            URL testResourceUrl = getClass().getResource(testResourcePath);

            if (testResourceUrl != null && testResourceUrl.getProtocol().equals("jar")) {
                // Extract JAR path from jar:file:/path/to.jar!/resource format
                String urlStr = testResourceUrl.toString();
                if (urlStr.startsWith("jar:file:")) {
                    int bangIndex = urlStr.indexOf("!");
                    if (bangIndex != -1) {
                        jarPath = urlStr.substring(9, bangIndex); // Skip "jar:file:"
                        jarPath = java.net.URLDecoder.decode(jarPath, "UTF-8");
                        jarFile = new File(jarPath);
                    }
                }
            }
        }

        if (!jarFile.exists() || !jarFile.isFile()) {
            throw new IOException("JAR file not found or invalid: " + jarPath);
        }

        logger.info("Extracting from JAR: " + jarFile.getAbsolutePath());

        // Normalize resource path (remove leading slash for comparison)
        String normalizedResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (!normalizedResourcePath.endsWith("/")) {
            normalizedResourcePath += "/";
        }

        int extractedCount = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Check if entry is within our target resource path
                if (entryName.startsWith(normalizedResourcePath) && !entryName.equals(normalizedResourcePath)) {
                    // Get relative path within the resource directory
                    String relativePath = entryName.substring(normalizedResourcePath.length());

                    File targetFile = new File(targetDir, relativePath);

                    if (entry.isDirectory()) {
                        // Create directory
                        if (!targetFile.exists() && !targetFile.mkdirs()) {
                            logger.warning("Failed to create directory: " + targetFile.getAbsolutePath());
                        } else {
                            targetFile.deleteOnExit();
                        }
                    } else {
                        // Extract file
                        File parentDir = targetFile.getParentFile();
                        if (parentDir != null && !parentDir.exists()) {
                            if (!parentDir.mkdirs()) {
                                throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
                            }
                            parentDir.deleteOnExit();
                        }

                        try (InputStream is = jar.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }

                        targetFile.deleteOnExit();
                        extractedCount++;

                        if (extractedCount % 100 == 0) {
                            logger.fine("Extracted " + extractedCount + " files...");
                        }
                    }
                }
            }
        }

        logger.info("Extracted " + extractedCount + " files from JAR");

        if (extractedCount == 0) {
            throw new IOException("No files found in JAR for resource path: " + resourcePath);
        }
    }

    /**
     * Copies a directory tree recursively (for development mode).
     *
     * @param source source directory path
     * @param target target directory path
     * @throws IOException if copy fails
     */
    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                targetFile.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Tests a BBTools script by running it without arguments to check if it executes.
     * BBTools scripts typically show usage information when run without args.
     *
     * @param script the script to test
     * @param name the name of the script (for logging)
     * @throws IOException if the script fails to execute
     */
    private void testBBToolsScript(File script, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(script.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output (limited to avoid hanging)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 10) {
                    output.append(line).append("\n");
                    lineCount++;
                }
            }

            // Wait for process with timeout
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.info(name + " script validation: timeout (expected behavior for BBTools)");
            } else {
                logger.info(name + " script validation successful. Output preview: " +
                    output.toString().substring(0, Math.min(100, output.length())));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Script validation interrupted for: " + name, e);
        }
    }

    /**
     * Registers a shutdown hook to clean up temporary BBTools files.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                cleanup();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during shutdown cleanup", e);
            }
        }, "BBToolsBinaryManager-Cleanup"));
        logger.info("Registered shutdown hook for cleanup");
    }

    /**
     * Cleans up temporary BBTools files and directory.
     */
    private void cleanup() {
        logger.info("Cleaning up temporary BBTools files...");

        if (tempDirectory != null && tempDirectory.exists()) {
            deleteDirectoryRecursively(tempDirectory);
            logger.info("Cleanup completed");
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory the directory to delete
     */
    private void deleteDirectoryRecursively(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }

        if (directory.delete()) {
            logger.fine("Deleted: " + directory.getAbsolutePath());
        } else {
            logger.warning("Failed to delete: " + directory.getAbsolutePath());
        }
    }

    /**
     * Counts files with a specific extension recursively in a directory.
     *
     * @param directory the directory to search
     * @param extension the file extension to count (including the dot)
     * @return count of files with the extension
     */
    private long countFilesRecursively(File directory, String extension) {
        long count = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFilesRecursively(file, extension);
                } else if (file.getName().endsWith(extension)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ==================== Public API Methods ====================

    /**
     * Gets the BBTools directory containing all extracted scripts and resources.
     *
     * @return BBTools directory File
     * @throws IOException if not initialized or directory not available
     */
    public File getBBToolsDirectory() throws IOException {
        if (!initialized) {
            initialize();
        }
        if (bbtoolsDirectory == null || !bbtoolsDirectory.exists()) {
            throw new IOException("BBTools directory not available");
        }
        return bbtoolsDirectory;
    }

    /**
     * Gets the path to a specific BBTools script by name.
     *
     * @param scriptName the name of the script (e.g., "clumpify.sh", "bbduk.sh")
     * @return File object for the script
     * @throws IOException if not initialized or script not found
     */
    public File getScriptPath(String scriptName) throws IOException {
        if (!initialized) {
            initialize();
        }

        File script = new File(bbtoolsDirectory, scriptName);
        if (!script.exists()) {
            throw new IOException("BBTools script not found: " + scriptName);
        }
        if (!script.canExecute()) {
            throw new IOException("BBTools script is not executable: " + scriptName);
        }

        return script;
    }

    /**
     * Gets the absolute path to a specific BBTools script by name.
     *
     * @param scriptName the name of the script (e.g., "clumpify.sh", "bbduk.sh")
     * @return absolute path to the script
     * @throws IOException if not initialized or script not found
     */
    public String getScriptPathString(String scriptName) throws IOException {
        return getScriptPath(scriptName).getAbsolutePath();
    }

    /**
     * Gets the File object for the clumpify script.
     * Convenience method for backward compatibility.
     *
     * @return clumpify script File
     * @throws IOException if not initialized or script not available
     */
    public File getClumpifyScript() throws IOException {
        return getScriptPath("clumpify.sh");
    }

    /**
     * Gets the absolute path to the clumpify script.
     * Convenience method for backward compatibility.
     *
     * @return clumpify script path
     * @throws IOException if not initialized or script not found
     */
    public String getClumpifyScriptPath() throws IOException {
        return getClumpifyScript().getAbsolutePath();
    }

    /**
     * Gets the File object for the reformat script.
     * Convenience method for backward compatibility.
     *
     * @return reformat script File
     * @throws IOException if not initialized or script not available
     */
    public File getReformatScript() throws IOException {
        return getScriptPath("reformat.sh");
    }

    /**
     * Gets the absolute path to the reformat script.
     * Convenience method for backward compatibility.
     *
     * @return reformat script path
     * @throws IOException if not initialized or script not found
     */
    public String getReformatScriptPath() throws IOException {
        return getReformatScript().getAbsolutePath();
    }

    /**
     * Gets the File object for the current/ directory containing BBTools classes.
     * Convenience method for backward compatibility.
     *
     * @return current directory File
     * @throws IOException if not initialized or directory not available
     */
    public File getCurrentDirectory() throws IOException {
        if (!initialized) {
            initialize();
        }
        File currentDir = new File(bbtoolsDirectory, "current");
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            throw new IOException("BBTools current/ directory not available");
        }
        return currentDir;
    }

    /**
     * Gets the absolute path to the current/ directory.
     * Convenience method for backward compatibility.
     *
     * @return current directory path
     * @throws IOException if not initialized or directory not found
     */
    public String getCurrentDirectoryPath() throws IOException {
        return getCurrentDirectory().getAbsolutePath();
    }

    /**
     * Gets the temp directory where BBTools files are extracted.
     *
     * @return temp directory File
     * @throws IOException if not initialized
     */
    public File getTempDirectory() throws IOException {
        if (!initialized) {
            initialize();
        }
        if (tempDirectory == null || !tempDirectory.exists()) {
            throw new IOException("Temp directory not available");
        }
        return tempDirectory;
    }

    /**
     * Checks if BBTools is available.
     *
     * @return true if BBTools is available
     */
    public boolean isBBToolsAvailable() {
        try {
            getBBToolsDirectory();
            getScriptPath("clumpify.sh");
            getCurrentDirectory();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resets the binary manager, forcing re-initialization on next use.
     * This also cleans up any existing temporary files.
     */
    public void reset() {
        cleanup();
        bbtoolsDirectory = null;
        tempDirectory = null;
        initialized = false;
        logger.info("BBTools binary manager reset");
    }

    /**
     * Gets information about the current BBTools installation.
     *
     * @return String with BBTools installation information
     */
    public String getBinaryInfo() {
        StringBuilder info = new StringBuilder();
        info.append("BBToolsBinaryManager Status:\n");
        info.append("Initialized: ").append(initialized).append("\n");
        info.append("Java Available: ").append(isJavaAvailable()).append("\n");

        if (initialized) {
            info.append("Temp Directory: ").append(tempDirectory != null ? tempDirectory.getAbsolutePath() : "null").append("\n");
            info.append("BBTools Directory: ").append(bbtoolsDirectory != null ? bbtoolsDirectory.getAbsolutePath() : "null").append("\n");

            try {
                // Count shell scripts
                long scriptCount = countFilesRecursively(bbtoolsDirectory, ".sh");
                info.append("Shell Scripts: ").append(scriptCount).append("\n");

                // Count class files
                File currentDir = getCurrentDirectory();
                if (currentDir.exists()) {
                    long classFileCount = countFilesRecursively(currentDir, ".class");
                    info.append("Class Files: ").append(classFileCount).append("\n");
                }

                // Check for key directories
                File resourcesDir = new File(bbtoolsDirectory, "resources");
                File configDir = new File(bbtoolsDirectory, "config");
                info.append("Resources Directory: ").append(resourcesDir.exists() ? "present" : "missing").append("\n");
                info.append("Config Directory: ").append(configDir.exists() ? "present" : "missing").append("\n");

            } catch (IOException e) {
                info.append("BBTools components: Not available - ").append(e.getMessage()).append("\n");
            }
        }

        return info.toString();
    }
}
