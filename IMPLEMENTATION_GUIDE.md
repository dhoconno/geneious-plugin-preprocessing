# Implementation Guide - Next Steps

This guide provides a roadmap for implementing the fastp plugin functionality.

## Quick Start

```bash
# Navigate to project
cd /Users/dho/Documents/geneious-plugin-fastp

# Verify build works
ant clean compile

# Make changes to Java files in src/
# Then rebuild
ant distribute

# Test in Geneious
cp build/FastpPlugin.gplugin ~/Library/Application\ Support/Geneious\ Prime/plugins/
# Restart Geneious
```

---

## Implementation Order (Recommended)

### Step 1: FastpOptions.java (1-2 days)

**Why First:** The UI needs to be functional before you can test the operation.

**Tasks:**
1. Study the Geneious Options API
   ```bash
   open /Users/dho/Downloads/geneious-2025.2.2-devkit/api-javadoc/com/biomatters/geneious/publicapi/plugin/Options.html
   ```

2. Look at examples:
   ```bash
   # Find examples that use Options
   grep -r "extends Options" /Users/dho/Downloads/geneious-2025.2.2-devkit/examples/
   ```

3. Implement basic options first:
   - Tool selection dropdown
   - Thread count
   - Quality threshold
   - Length filters

4. Test by installing plugin and opening options dialog

5. Add advanced options:
   - Adapter sequences
   - Cutting options
   - Complexity filtering
   - UMI processing

6. Add validation and tooltips

**Reference Code Pattern:**
```java
// Integer option
addIntegerOption(THREADS, "Number of threads:", 4, 1, 16);

// Combo box
addComboBoxOption(TOOL_MODE, "Tool Selection",
    new String[]{"Auto-detect", "Fastp", "Fastplong"},
    "Auto-detect");

// Boolean checkbox
addBooleanOption(DETECT_ADAPTER, "Auto-detect adapters", true);

// String field
addStringOption(ADAPTER_R1, "Custom adapter R1:", "");

// Section label
addLabel("Quality Filtering Options");
```

**Testing:**
- Install plugin in Geneious
- Select any document
- Right-click → "Run Fastp QC"
- Verify options dialog appears with your fields
- Check that values are reasonable

---

### Step 2: FastpBinaryManager.java (1 day)

**Why Second:** Need to locate binaries before you can execute them.

**Tasks:**
1. Implement PATH search:
   ```java
   String pathEnv = System.getenv("PATH");
   String[] paths = pathEnv.split(File.pathSeparator);
   for (String path : paths) {
       File binary = new File(path, "fastp");
       if (binary.exists() && binary.canExecute()) {
           return binary.getAbsolutePath();
       }
   }
   ```

2. Add platform detection:
   ```java
   String os = System.getProperty("os.name").toLowerCase();
   boolean isWindows = os.contains("win");
   boolean isMac = os.contains("mac");
   boolean isLinux = os.contains("linux");

   String binaryName = isWindows ? "fastp.exe" : "fastp";
   ```

3. Add version checking:
   ```java
   ProcessBuilder pb = new ProcessBuilder(binaryPath, "--version");
   Process p = pb.start();
   BufferedReader reader = new BufferedReader(
       new InputStreamReader(p.getInputStream())
   );
   String version = reader.readLine();
   ```

4. Handle common installation paths:
   ```java
   // macOS Homebrew
   "/opt/homebrew/bin/fastp"
   "/usr/local/bin/fastp"

   // Linux
   "/usr/bin/fastp"
   "/usr/local/bin/fastp"

   // Windows
   "C:\\Program Files\\fastp\\fastp.exe"
   ```

**Testing:**
- Install fastp on your system: `brew install fastp` (macOS)
- Test binary detection:
  ```java
  FastpBinaryManager mgr = FastpBinaryManager.getInstance();
  mgr.initialize();
  System.out.println("Fastp path: " + mgr.getFastpPath());
  System.out.println("Version: " + mgr.getFastpVersion());
  ```

---

### Step 3: FastpExecutor.java (2-3 days)

**Why Third:** Need to build and execute commands.

**Tasks:**
1. Complete `buildSingleEndCommand()`:
   ```java
   List<String> command = new ArrayList<>();
   command.add(binaryPath);
   command.add("-i"); command.add(inputFile.getAbsolutePath());
   command.add("-o"); command.add(outputFile.getAbsolutePath());
   command.add("-j"); command.add(jsonReport.getAbsolutePath());
   command.add("-h"); command.add(htmlReport.getAbsolutePath());

   // Add options
   command.add("-w"); command.add(String.valueOf(options.getThreads()));
   command.add("-q"); command.add(String.valueOf(options.getQualifiedQualityPhred()));
   // ... add all other options
   ```

2. Complete `buildPairedEndCommand()` (similar but with -I and -O)

3. Implement `executeCommand()`:
   ```java
   ProcessBuilder pb = new ProcessBuilder(command);
   pb.redirectErrorStream(true); // Merge stderr into stdout
   process = pb.start();

   // Read output
   BufferedReader reader = new BufferedReader(
       new InputStreamReader(process.getInputStream())
   );

   String line;
   while ((line = reader.readLine()) != null) {
       // Log or parse for progress
       if (cancelled) {
           process.destroy();
           throw new DocumentOperationException("Cancelled");
       }
   }

   int exitCode = process.waitFor();
   if (exitCode != 0) {
       throw new DocumentOperationException("Fastp failed with exit code " + exitCode);
   }
   ```

4. Add progress parsing (fastp outputs progress to stderr)

**Testing:**
- Create test FASTQ files
- Test command building
- Test execution with real fastp
- Verify output files are created
- Check JSON and HTML reports

---

### Step 4: FastpOperation.java (3-5 days)

**Why Fourth:** This is the main workflow coordinator.

**Tasks:**

#### 4.1 Document Validation
```java
private void validateDocuments(AnnotatedPluginDocument[] documents)
        throws DocumentOperationException {
    for (AnnotatedPluginDocument doc : documents) {
        PluginDocument pluginDoc = doc.getDocument();
        if (!(pluginDoc instanceof SequenceDocument)) {
            throw new DocumentOperationException(
                "Selected document is not a sequence: " + doc.getName()
            );
        }
        // TODO: Check if it's specifically FASTQ
        // May need to check file format or document properties
    }
}
```

#### 4.2 Paired-End Detection
```java
private boolean isPairedEnd(AnnotatedPluginDocument[] documents) {
    if (documents.length != 2) return false;

    String name1 = documents[0].getName();
    String name2 = documents[1].getName();

    // Check for R1/R2 naming
    boolean hasR1R2 = (name1.contains("_R1") && name2.contains("_R2")) ||
                      (name1.contains("_R2") && name2.contains("_R1"));

    // Check for _1/_2 naming
    boolean has12 = (name1.contains("_1") && name2.contains("_2")) ||
                    (name1.contains("_2") && name2.contains("_1"));

    return hasR1R2 || has12;
}
```

#### 4.3 Long-Read Detection
```java
private boolean isLongRead(AnnotatedPluginDocument[] documents) {
    // Sample first document to estimate read length
    SequenceDocument seq = (SequenceDocument) documents[0].getDocument();
    int length = seq.getSequenceLength();

    // If average read > 500bp, probably long reads
    return length > 500;
}
```

#### 4.4 Export to FASTQ
```java
private void exportToFastq(AnnotatedPluginDocument doc, File outputFile)
        throws IOException {
    SequenceDocument seq = (SequenceDocument) doc.getDocument();

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
        // Write FASTQ format
        writer.write("@" + seq.getName() + "\n");
        writer.write(seq.getSequenceString() + "\n");
        writer.write("+\n");

        // TODO: Get quality scores if available
        // For now, write dummy quality scores
        String quality = "I".repeat(seq.getSequenceLength());
        writer.write(quality + "\n");
    }
}
```

#### 4.5 Import Results
```java
private AnnotatedPluginDocument importProcessedSequences(File fastqFile)
        throws IOException {
    // Use Geneious document importers
    // TODO: Find the right importer for FASTQ

    // Placeholder:
    throw new IOException("Import not yet implemented");
}
```

#### 4.6 Main performOperation() Implementation
```java
@Override
public List<AnnotatedPluginDocument> performOperation(
        AnnotatedPluginDocument[] documents,
        ProgressListener progressListener,
        Options options) throws DocumentOperationException {

    try {
        progressListener.setMessage("Validating input...");
        validateDocuments(documents);

        FastpOptions fastpOptions = (FastpOptions) options;

        // Detect read type
        boolean isPaired = isPairedEnd(documents);
        boolean isLong = isLongRead(documents);

        // Get binary
        FastpBinaryManager binMgr = FastpBinaryManager.getInstance();
        String binaryPath = isLong ? binMgr.getFastplongPath() : binMgr.getFastpPath();

        // Create temp directory
        File tempDir = Files.createTempDirectory("fastp").toFile();

        try {
            // Export sequences
            progressListener.setMessage("Exporting sequences...");
            File inputFile = new File(tempDir, "input.fastq");
            exportToFastq(documents[0], inputFile);

            File outputFile = new File(tempDir, "output.fastq");
            File jsonReport = new File(tempDir, "report.json");
            File htmlReport = new File(tempDir, "report.html");

            // Execute fastp
            progressListener.setMessage("Running fastp...");
            FastpExecutor executor = new FastpExecutor();
            int exitCode = executor.executeSingleEnd(
                binaryPath, inputFile, outputFile,
                jsonReport, htmlReport, fastpOptions, progressListener
            );

            if (exitCode != 0) {
                throw new DocumentOperationException("Fastp failed with exit code " + exitCode);
            }

            // Import results
            progressListener.setMessage("Importing results...");
            List<AnnotatedPluginDocument> results = new ArrayList<>();

            // Import processed sequences
            AnnotatedPluginDocument processedSeq = importProcessedSequences(outputFile);
            results.add(processedSeq);

            // Import HTML report
            // TODO: Import as HTMLDocument or similar

            return results;

        } finally {
            // Cleanup temp files
            deleteDirectory(tempDir);
        }

    } catch (IOException e) {
        throw new DocumentOperationException("Operation failed: " + e.getMessage(), e);
    }
}
```

**Testing:**
- Test with single-end FASTQ
- Test with paired-end FASTQ
- Test with long-read FASTQ
- Verify output is imported correctly
- Check HTML report is accessible

---

## Testing Strategy

### Unit Testing

Create test class: `src/test/java/com/biomatters/plugins/fastp/FastpPluginTest.java`

```java
public class FastpPluginTest {

    @Test
    public void testBinaryDetection() throws Exception {
        FastpBinaryManager mgr = FastpBinaryManager.getInstance();
        mgr.initialize();
        assertNotNull(mgr.getFastpPath());
    }

    @Test
    public void testCommandBuilding() {
        FastpOptions options = new FastpOptions();
        FastpExecutor executor = new FastpExecutor();
        // Test command building
    }

    // More tests...
}
```

### Integration Testing

1. Create test FASTQ files in `test-data/`
2. Test full workflow:
   - Load in Geneious
   - Run operation
   - Verify output

### Manual Testing Checklist

- [ ] Plugin loads without errors
- [ ] Options dialog displays correctly
- [ ] All options are functional
- [ ] Single-end FASTQ processing works
- [ ] Paired-end FASTQ processing works
- [ ] Long-read processing works
- [ ] Progress is displayed
- [ ] Cancellation works
- [ ] Error messages are clear
- [ ] Output sequences are correct
- [ ] HTML report is generated
- [ ] Temporary files are cleaned up

---

## Common Issues & Solutions

### Issue: Plugin doesn't appear in Geneious

**Solution:**
- Check plugin.properties has correct plugin-name
- Verify JAR contains plugin.properties
- Check Geneious logs for errors
- Restart Geneious

### Issue: Compilation errors

**Solution:**
- Verify DevKit path in build.xml is correct
- Check Java version (needs 11)
- Look at compiler output for details

### Issue: Binary not found

**Solution:**
- Install fastp: `brew install fastp` (macOS)
- Add to PATH
- Or implement manual path configuration

### Issue: FASTQ import fails

**Solution:**
- Use Geneious FASTQ importer classes
- Check DevKit examples for file import
- May need to use DocumentUtilities

---

## API References You'll Need

### Most Important Classes

```java
// Plugin framework
import com.biomatters.geneious.publicapi.plugin.GeneiousPlugin;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;

// Documents
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;

// Utilities
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;

// Progress
import jebl.util.ProgressListener;
```

### Key Methods to Research

Look these up in the API docs:

```java
// Creating sequences
DefaultNucleotideSequence(String name, String description, String sequence, Date date)

// Creating annotated documents
DocumentUtilities.createAnnotatedPluginDocuments(List<PluginDocument> docs)

// Options API
Options.addIntegerOption(String name, String label, int defaultValue, int min, int max)
Options.addStringOption(String name, String label, String defaultValue)
Options.addBooleanOption(String name, String label, boolean defaultValue)
Options.addComboBoxOption(String name, String label, String[] values, String defaultValue)

// Progress updates
ProgressListener.setMessage(String message)
ProgressListener.setProgress(double fraction) // 0.0 to 1.0
```

---

## Development Workflow

### Typical Development Cycle

1. Edit Java source file
2. Compile: `ant compile`
3. If errors, fix and goto 2
4. Build: `ant distribute`
5. Install: `cp build/FastpPlugin.gplugin ~/Library/Application\ Support/Geneious\ Prime/plugins/`
6. Restart Geneious
7. Test
8. Repeat

### Using an IDE (Recommended)

**IntelliJ IDEA:**
1. Open project
2. Import from build.xml
3. Add DevKit JARs to classpath
4. Create run configuration pointing to Geneious
5. Set breakpoints and debug

**Eclipse:**
1. Import as "Existing Project"
2. Configure build path with DevKit JARs
3. Create launch configuration for Geneious

---

## Code Quality Checklist

Before considering implementation complete:

- [ ] All TODO comments resolved or documented
- [ ] JavaDoc comments for all public methods
- [ ] Error handling with meaningful messages
- [ ] Resource cleanup (files, processes)
- [ ] Thread safety considered
- [ ] Memory efficiency considered
- [ ] Platform compatibility tested
- [ ] Code follows Java conventions
- [ ] No hardcoded paths (except DevKit in build.xml)
- [ ] Logging for debugging
- [ ] User-friendly error messages

---

## Getting Help

### Geneious Support
- DevKit PDF guide (comprehensive)
- API Javadoc (reference)
- Example plugins (working code)

### Fastp Documentation
- GitHub README: https://github.com/OpenGene/fastp
- Run `fastp --help` for all options
- Check JSON output format

### General Java
- Oracle Java 11 docs
- Stack Overflow
- Apache Ant documentation

---

## Success Criteria

You'll know implementation is complete when:

1. ✓ Plugin loads in Geneious without errors
2. ✓ Options dialog is fully functional with all parameters
3. ✓ Binary detection works on your platform
4. ✓ Can process single-end FASTQ files
5. ✓ Can process paired-end FASTQ files
6. ✓ Can process long-read FASTQ files
7. ✓ Processed sequences are imported correctly
8. ✓ HTML report is accessible
9. ✓ JSON statistics are parsed
10. ✓ Progress is displayed during processing
11. ✓ Errors are handled gracefully
12. ✓ Temporary files are cleaned up
13. ✓ Can cancel operation mid-execution
14. ✓ Works on multiple platforms (Windows, macOS, Linux)

---

## Estimated Timeline

**Minimum Viable Product (MVP):**
- FastpOptions: 1-2 days
- FastpBinaryManager: 1 day
- FastpExecutor: 2-3 days
- FastpOperation: 3-5 days
- Testing & debugging: 2-3 days

**Total: 9-14 days**

**Full Featured Version:**
- Add all advanced options: +2 days
- Multi-platform testing: +2 days
- Comprehensive error handling: +1 day
- Documentation: +1 day
- Polish and optimization: +2 days

**Total: 17-22 days**

---

## Quick Reference Commands

```bash
# Build
ant clean compile distribute

# Install in Geneious
cp build/FastpPlugin.gplugin ~/Library/Application\ Support/Geneious\ Prime/plugins/

# View API docs
open /Users/dho/Downloads/geneious-2025.2.2-devkit/api-javadoc/index.html

# Test fastp locally
fastp -i test.fastq -o output.fastq -j report.json -h report.html

# Find Geneious plugin directory
ls ~/Library/Application\ Support/Geneious\ Prime/plugins/

# View Geneious logs (if errors)
ls ~/Library/Application\ Support/Geneious\ Prime/logs/
```

---

## Good Luck!

The skeleton is complete and well-documented. Follow this guide step-by-step, and you'll have a working fastp plugin for Geneious Prime.

Remember:
- Start with FastpOptions (easiest to test)
- Test frequently
- Use the DevKit examples as reference
- Don't hesitate to simplify if needed (start basic, add features later)

Happy coding!
