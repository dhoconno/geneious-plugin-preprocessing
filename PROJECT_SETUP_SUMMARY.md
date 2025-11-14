# Geneious Fastp Plugin - Project Setup Summary

## Project Successfully Created

The complete Geneious Prime plugin project structure has been created and verified.

**Working Directory:** `/Users/dho/Documents/geneious-plugin-fastp`
**DevKit Location:** `/Users/dho/Downloads/geneious-2025.2.2-devkit`

---

## Directory Structure

```
geneious-plugin-fastp/
├── .gitignore                           # Git ignore file
├── README.md                            # Comprehensive project documentation
├── PROJECT_SETUP_SUMMARY.md            # This file
├── plugin.properties                    # Plugin metadata
├── build.xml                            # Ant build configuration
│
├── src/                                 # Java source files
│   └── com/biomatters/plugins/fastp/
│       ├── FastpPlugin.java            # Main plugin class (DONE)
│       ├── FastpOperation.java         # Operation implementation (SKELETON)
│       ├── FastpOptions.java           # Options UI panel (SKELETON)
│       ├── FastpBinaryManager.java     # Binary management (SKELETON)
│       └── FastpExecutor.java          # Command execution (SKELETON)
│
├── build/                               # Build output (created by Ant)
│   ├── FastpPlugin.jar                 # Compiled plugin JAR
│   └── FastpPlugin.gplugin             # Distributable plugin file
│
└── classes/                             # Compiled class files (created by Ant)
    └── com/biomatters/plugins/fastp/
        ├── FastpPlugin.class
        ├── FastpOperation.class
        ├── FastpOptions.class
        ├── FastpBinaryManager.class
        └── FastpExecutor.class
```

---

## Files Created

### 1. plugin.properties
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/plugin.properties`

**Contents:**
```properties
plugin-name=com.biomatters.plugins.fastp.FastpPlugin
short-plugin-name=FastpPlugin
version=1.0.0
author=Your Name
description=Fastp/Fastplong quality control and preprocessing plugin for Geneious Prime
```

**Purpose:** Required metadata file that Geneious reads to load the plugin.

---

### 2. build.xml
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/build.xml`

**Build System:** Apache Ant (standard for Geneious plugins)

**Key Features:**
- Targets Java 11 (Geneious requirement)
- References Geneious DevKit libraries
- Creates .gplugin distributable file

**Available Targets:**
```bash
ant clean       # Remove build artifacts
ant prepare     # Create build directories
ant compile     # Compile Java sources
ant build       # Create JAR file
ant distribute  # Create .gplugin file (default target)
```

**Classpath References:**
- `GeneiousPublicAPI.jar` - Core Geneious API
- `jdom.jar` - XML processing
- `jebl.jar` - JEBL bioinformatics library
- `commons-io-*.jar` - File utilities
- `commons-lang3-*.jar` - Java utilities

All JARs located in: `/Users/dho/Downloads/geneious-2025.2.2-devkit/examples/GeneiousFiles/lib/`

---

### 3. Java Classes

#### FastpPlugin.java
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/src/com/biomatters/plugins/fastp/FastpPlugin.java`

**Status:** ✓ COMPLETE

**Purpose:** Main plugin entry point

**Key Methods:**
- `getName()` - Returns "Fastp Quality Control"
- `getDescription()` - Plugin description
- `getAuthors()` - Author information
- `getVersion()` - Returns "1.0.0"
- `getMinimumApiVersion()` - Returns "4.0"
- `getMaximumApiVersion()` - Returns 4
- `getDocumentOperations()` - Returns array containing FastpOperation

**Extends:** `com.biomatters.geneious.publicapi.plugin.GeneiousPlugin`

---

#### FastpOperation.java
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/src/com/biomatters/plugins/fastp/FastpOperation.java`

**Status:** ⚠ SKELETON (needs implementation)

**Purpose:** Implements the actual QC operation that users invoke

**Key Methods:**
- `getActionOptions()` - Configures menu appearance (toolbar, popup menu)
- `getHelp()` - Help text for the operation
- `getSelectionSignatures()` - Defines valid input types (currently accepts any document)
- `getOptions()` - Returns FastpOptions panel for user configuration
- `performOperation()` - **NEEDS IMPLEMENTATION** - Main execution method

**Extends:** `com.biomatters.geneious.publicapi.plugin.DocumentOperation`

**TODO Items:**
1. Implement document validation (must be FASTQ)
2. Detect paired-end vs single-end
3. Detect short-read vs long-read
4. Export sequences to temporary FASTQ files
5. Build fastp command line
6. Execute fastp binary
7. Parse JSON output
8. Import processed sequences
9. Import HTML report
10. Clean up temporary files

---

#### FastpOptions.java
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/src/com/biomatters/plugins/fastp/FastpOptions.java`

**Status:** ⚠ SKELETON (needs implementation)

**Purpose:** Creates the user interface for configuring fastp parameters

**Extends:** `com.biomatters.geneious.publicapi.plugin.Options`

**Currently:** Shows placeholder message, needs full implementation

**TODO - Add Option Fields:**
1. Tool Selection (auto-detect, fastp, fastplong)
2. Threading (number of threads)
3. Quality Filtering
   - Qualified quality phred (default: 15)
   - Unqualified percent limit (default: 40)
   - N base limit (default: 5)
   - Average quality requirement (default: 0)
4. Length Filtering
   - Minimum length (default: 15)
   - Maximum length (default: 0, disabled)
5. Adapter Trimming
   - Auto-detect (default: true)
   - Custom adapter R1
   - Custom adapter R2
6. Per-read Quality Cutting
   - Cut front (5' end)
   - Cut tail (3' end)
   - Cut window size (default: 4)
   - Cut mean quality (default: 20)
7. Base Correction (paired-end)
8. Complexity Filtering
9. UMI Processing options

**TODO - Add Getter Methods:**
- All configuration values need getter methods
- Validation logic for user input

---

#### FastpBinaryManager.java
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/src/com/biomatters/plugins/fastp/FastpBinaryManager.java`

**Status:** ⚠ SKELETON (needs implementation)

**Purpose:** Manages fastp/fastplong binary detection and validation

**Pattern:** Singleton

**Key Methods:**
- `getInstance()` - Get singleton instance
- `initialize()` - Detect and validate binaries
- `getFastpPath()` - Returns path to fastp binary
- `getFastplongPath()` - Returns path to fastplong binary
- `isFastpAvailable()` - Check if fastp is available
- `isFastplongAvailable()` - Check if fastplong is available
- `getFastpVersion()` - Get fastp version
- `setBinaryPaths()` - Allow manual configuration

**TODO - Binary Detection:**
1. Check bundled binaries (if we bundle them)
2. Search system PATH
3. Check common installation locations:
   - macOS: `/usr/local/bin/`, `/opt/homebrew/bin/`
   - Linux: `/usr/bin/`, `/usr/local/bin/`
   - Windows: `C:\Program Files\`, user PATH
4. Validate executables (permissions, version check)
5. Handle platform-specific issues

---

#### FastpExecutor.java
**Location:** `/Users/dho/Documents/geneious-plugin-fastp/src/com/biomatters/plugins/fastp/FastpExecutor.java`

**Status:** ⚠ SKELETON (needs implementation)

**Purpose:** Executes fastp commands and manages process I/O

**Key Methods:**
- `executeSingleEnd()` - Run fastp on single-end reads
- `executePairedEnd()` - Run fastp on paired-end reads
- `buildSingleEndCommand()` - Construct command for single-end
- `buildPairedEndCommand()` - Construct command for paired-end
- `executeCommand()` - Execute and monitor process
- `cancel()` - Cancel running process

**TODO - Implementation:**
1. Build complete command lines from options
2. Execute process using ProcessBuilder
3. Capture stdout/stderr
4. Parse progress from fastp output
5. Update ProgressListener
6. Handle cancellation
7. Return exit code
8. Error handling for non-zero exits

**Fastp Command Structure:**
```bash
# Single-end
fastp -i input.fastq -o output.fastq -j report.json -h report.html [options]

# Paired-end
fastp -i in.R1.fastq -I in.R2.fastq -o out.R1.fastq -O out.R2.fastq \
      -j report.json -h report.html [options]
```

**Common Options to Support:**
- `-q, --qualified_quality_phred` - Quality threshold
- `-u, --unqualified_percent_limit` - Unqualified base limit
- `-n, --n_base_limit` - N base limit
- `-l, --length_required` - Minimum length
- `--length_limit` - Maximum length
- `-w, --thread` - Thread count
- `--detect_adapter_for_pe` - Adapter detection (PE)
- And many more...

---

## Build Verification

### Build Status: ✓ SUCCESS

The project has been successfully built and verified:

```bash
$ ant clean
BUILD SUCCESSFUL

$ ant compile
Compiling 5 source files
BUILD SUCCESSFUL

$ ant distribute
Building jar: .../build/FastpPlugin.jar
Created .../build/FastpPlugin.gplugin
BUILD SUCCESSFUL
```

### Plugin JAR Contents

```
META-INF/MANIFEST.MF
com/biomatters/plugins/fastp/FastpBinaryManager.class
com/biomatters/plugins/fastp/FastpExecutor.class
com/biomatters/plugins/fastp/FastpOperation.class
com/biomatters/plugins/fastp/FastpOptions.class
com/biomatters/plugins/fastp/FastpPlugin.class
plugin.properties
```

All classes compiled successfully. The plugin is ready for implementation.

---

## How to Reference Geneious API

### Required JARs (Already Configured in build.xml)

**GeneiousPublicAPI.jar**
- Location: `${devkit}/examples/GeneiousFiles/lib/GeneiousPublicAPI.jar`
- Contains: All public API classes and interfaces
- Key Packages:
  - `com.biomatters.geneious.publicapi.plugin.*` - Plugin framework
  - `com.biomatters.geneious.publicapi.documents.*` - Document types
  - `com.biomatters.geneious.publicapi.implementations.*` - Utility implementations

**jebl.jar**
- Location: `${devkit}/examples/GeneiousFiles/lib/jebl.jar`
- Contains: `jebl.util.ProgressListener` and other utilities

**jdom.jar**
- Location: `${devkit}/examples/GeneiousFiles/lib/jdom.jar`
- Contains: XML parsing utilities

### API Documentation

**Javadoc:**
```
/Users/dho/Downloads/geneious-2025.2.2-devkit/api-javadoc/
```
Open `index.html` in a browser for complete API documentation.

**PDF Guide:**
```
/Users/dho/Downloads/geneious-2025.2.2-devkit/PhobosPluginDevelopment.pdf
```
Comprehensive plugin development guide.

**Example Plugins:**
```
/Users/dho/Downloads/geneious-2025.2.2-devkit/examples/
```
- `HelloWorld/` - Basic plugin structure
- `ReverseSequence/` - Simple operation example
- Many others demonstrating different features

### Key API Classes

**Plugin Base Classes:**
- `GeneiousPlugin` - Base class for all plugins
- `DocumentOperation` - Base for operations on documents
- `Options` - Base for configuration UIs

**Document Types:**
- `AnnotatedPluginDocument` - Document wrapper with metadata
- `PluginDocument` - Base document interface
- `SequenceDocument` - Sequence data interface
- `NucleotideSequenceDocument` - Nucleotide sequences

**UI Classes:**
- `GeneiousActionOptions` - Configure menu appearance
- `DocumentSelectionSignature` - Define valid inputs

**Utilities:**
- `DocumentUtilities` - Helper methods for documents
- `ProgressListener` - Progress monitoring interface

---

## Installation in Geneious

### Option 1: Install from File

1. Build the plugin:
   ```bash
   cd /Users/dho/Documents/geneious-plugin-fastp
   ant distribute
   ```

2. In Geneious Prime:
   - Go to **Tools → Plugins**
   - Click **Install Plugin from File**
   - Select `build/FastpPlugin.gplugin`
   - Restart Geneious

### Option 2: Copy to Plugins Directory

1. Build the plugin:
   ```bash
   cd /Users/dho/Documents/geneious-plugin-fastp
   ant distribute
   ```

2. Copy to Geneious plugins directory:
   ```bash
   # macOS
   cp build/FastpPlugin.gplugin ~/Library/Application\ Support/Geneious\ Prime/plugins/

   # Linux
   cp build/FastpPlugin.gplugin ~/.geneious_prime/plugins/

   # Windows
   copy build\FastpPlugin.gplugin %APPDATA%\Geneious Prime\plugins\
   ```

3. Restart Geneious Prime

### Verification

After installation and restart:
1. The plugin should appear in **Tools → Plugins**
2. "Run Fastp QC" should appear in the main toolbar
3. When you select a document, "Run Fastp QC" should appear in the right-click menu

**Note:** The operation will currently show a "not yet implemented" error when run. This is expected - the skeleton shows the placeholder message we added.

---

## Next Development Steps

### Phase 1: FastpOptions Implementation (Priority: HIGH)
- [ ] Add all option fields using Geneious Options API
- [ ] Implement getter methods for all parameters
- [ ] Add validation logic
- [ ] Add tooltips and help text
- [ ] Test options panel displays correctly

### Phase 2: FastpBinaryManager Implementation (Priority: HIGH)
- [ ] Implement binary detection for macOS
- [ ] Implement binary detection for Windows
- [ ] Implement binary detection for Linux
- [ ] Add version checking (`fastp --version`)
- [ ] Add preference storage for custom paths
- [ ] Test on multiple platforms

### Phase 3: FastpExecutor Implementation (Priority: MEDIUM)
- [ ] Implement command building with all options
- [ ] Implement ProcessBuilder execution
- [ ] Add stdout/stderr capture
- [ ] Parse fastp progress output
- [ ] Update ProgressListener
- [ ] Add cancellation support
- [ ] Handle errors and non-zero exit codes

### Phase 4: FastpOperation Implementation (Priority: HIGH)
- [ ] Validate input documents (must be FASTQ)
- [ ] Detect paired-end reads (R1/R2 naming)
- [ ] Detect long-reads (average read length)
- [ ] Export sequences to temporary FASTQ files
- [ ] Call FastpExecutor
- [ ] Parse JSON output for statistics
- [ ] Import processed FASTQ files
- [ ] Import HTML report as document
- [ ] Add temporary file cleanup

### Phase 5: Testing (Priority: MEDIUM)
- [ ] Unit tests for each class
- [ ] Integration tests with sample FASTQ files
- [ ] Test single-end processing
- [ ] Test paired-end processing
- [ ] Test long-read processing
- [ ] Test error handling
- [ ] Test cancellation
- [ ] Test on multiple platforms

### Phase 6: Documentation (Priority: LOW)
- [ ] User guide
- [ ] Parameter reference
- [ ] Example workflows
- [ ] Screenshots
- [ ] Tutorial video

---

## Development Tips

### Compiling Frequently

```bash
# Quick compile and check for errors
ant compile

# Full build
ant distribute
```

### Debugging in Geneious

1. Set up IDE (IntelliJ IDEA recommended)
2. Import project from build.xml
3. Configure debug configuration pointing to Geneious executable
4. Set breakpoints in code
5. Run in debug mode

### Testing Without Full Install

```bash
# Quick test compile
ant test
```

### Viewing API Documentation

```bash
# Open Javadoc in browser
open /Users/dho/Downloads/geneious-2025.2.2-devkit/api-javadoc/index.html

# Read PDF guide
open /Users/dho/Downloads/geneious-2025.2.2-devkit/PhobosPluginDevelopment.pdf
```

### Example Code Reference

Look at example plugins for guidance:
```bash
# View ReverseSequence operation example
less /Users/dho/Downloads/geneious-2025.2.2-devkit/examples/ReverseSequence/src/com/biomatters/reversesequence/ReverseSeqPlugin.java
```

---

## Key Design Decisions

### 1. Package Structure
- **Package:** `com.biomatters.plugins.fastp`
- **Reason:** Follows Geneious plugin conventions
- **Classes:** 5 main classes with clear separation of concerns

### 2. Build System
- **Tool:** Apache Ant (not Maven)
- **Reason:** Standard for Geneious plugins, DevKit provides examples
- **Target:** Java 11 (Geneious requirement)

### 3. Binary Management
- **Strategy:** Singleton pattern
- **Reason:** Only need one instance checking binaries
- **Detection:** Multi-stage fallback (bundled → PATH → common locations)

### 4. Architecture Pattern
- **Pattern:** Separation of Concerns
- **Layers:**
  - Plugin registration (FastpPlugin)
  - UI and options (FastpOptions)
  - Workflow coordination (FastpOperation)
  - Binary management (FastpBinaryManager)
  - Process execution (FastpExecutor)

### 5. TODO-Driven Development
- **Approach:** Comprehensive TODO comments in all skeleton classes
- **Benefit:** Clear roadmap for implementation
- **Documentation:** JavaDoc explains what each class/method will do

---

## Resources

### Official Resources
- **Geneious DevKit:** `/Users/dho/Downloads/geneious-2025.2.2-devkit/`
- **API Javadoc:** `${devkit}/api-javadoc/index.html`
- **Plugin Development Guide:** `${devkit}/PhobosPluginDevelopment.pdf`
- **Example Plugins:** `${devkit}/examples/`

### Fastp Resources
- **Fastp GitHub:** https://github.com/OpenGene/fastp
- **Fastplong GitHub:** https://github.com/OpenGene/fastplong
- **Fastp Paper:** Chen et al., Bioinformatics 2018

### Build Tools
- **Apache Ant:** https://ant.apache.org/
- **Java 11 Documentation:** https://docs.oracle.com/en/java/javase/11/

---

## Status Summary

### ✓ Completed
- [x] Project directory structure created
- [x] Build configuration (build.xml) created and tested
- [x] Plugin metadata (plugin.properties) configured
- [x] Main plugin class (FastpPlugin.java) implemented
- [x] Skeleton classes created with comprehensive TODOs
- [x] README.md with full documentation
- [x] .gitignore configured
- [x] Successful compilation verified
- [x] .gplugin distributable created

### ⚠ In Progress (Skeleton State)
- [ ] FastpOperation.java - needs implementation
- [ ] FastpOptions.java - needs implementation
- [ ] FastpBinaryManager.java - needs implementation
- [ ] FastpExecutor.java - needs implementation

### ⏳ Not Started
- [ ] Unit tests
- [ ] Integration tests
- [ ] User documentation
- [ ] Example workflows
- [ ] Bundle fastp binaries with plugin (optional)

---

## Contact & Support

**Project Owner:** David Ho
**Version:** 1.0.0 (Skeleton)
**Created:** 2025-11-13
**Geneious Version:** 2025.2.2
**Java Version:** 11

---

## Conclusion

The Geneious Fastp plugin project structure is **complete and ready for implementation**.

All skeleton classes are in place with comprehensive TODO comments and documentation. The build system is configured and tested. The next phase is to implement the actual functionality in each skeleton class, starting with the high-priority components (FastpOptions, FastpBinaryManager, and FastpOperation).

The project follows Geneious plugin best practices and is structured for maintainability and extensibility.
