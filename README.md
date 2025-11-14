# Fastp/Fastplong Plugin for Geneious Prime

A Geneious Prime plugin that integrates fastp and fastplong for quality control and preprocessing of high-throughput sequencing data.

## Project Structure

```
geneious-plugin-fastp/
├── README.md                    # This file
├── plugin.properties            # Plugin metadata
├── build.xml                    # Ant build configuration
├── build/                       # Build output directory (created by Ant)
├── classes/                     # Compiled classes directory (created by Ant)
└── src/                         # Java source files
    └── com/
        └── biomatters/
            └── plugins/
                └── fastp/
                    ├── FastpPlugin.java           # Main plugin class
                    ├── FastpOperation.java        # Main operation implementation
                    ├── FastpOptions.java          # Options panel for user configuration
                    ├── FastpBinaryManager.java    # Binary detection and management
                    └── FastpExecutor.java         # Command execution and process handling
```

## Java Classes Overview

### 1. FastpPlugin.java
**Purpose:** Main plugin entry point that Geneious loads.

**Responsibilities:**
- Extends GeneiousPlugin base class
- Provides plugin metadata (name, version, description, authors)
- Registers document operations (FastpOperation)
- Defines API version compatibility

**Status:** Skeleton implemented with TODOs

### 2. FastpOperation.java
**Purpose:** Implements the actual QC operation that users invoke.

**Responsibilities:**
- Defines how the operation appears in Geneious UI (menus, toolbar)
- Validates input documents (must be FASTQ files)
- Determines processing mode (single-end vs paired-end, short vs long reads)
- Coordinates the entire workflow from input to output
- Creates and returns result documents

**Key Methods:**
- `getActionOptions()`: Configures menu placement and appearance
- `getSelectionSignatures()`: Defines valid input document types
- `getOptions()`: Returns the options panel
- `performOperation()`: Main execution method

**Status:** Skeleton implemented with comprehensive TODOs

### 3. FastpOptions.java
**Purpose:** Creates the user interface for configuring fastp parameters.

**Responsibilities:**
- Extends Geneious Options class
- Creates form fields for all fastp parameters
- Groups related options into logical sections
- Validates user input
- Provides getter methods for retrieving configured values

**Parameter Categories:**
- Tool selection (fastp vs fastplong)
- Quality filtering thresholds
- Length filtering limits
- Adapter trimming configuration
- Per-read quality cutting
- Base correction
- UMI processing
- Complexity filtering

**Status:** Skeleton implemented with TODOs for all option fields

### 4. FastpBinaryManager.java
**Purpose:** Manages fastp/fastplong binary detection and validation.

**Responsibilities:**
- Detects fastp and fastplong binaries on the system
- Handles platform-specific paths (Windows, macOS, Linux)
- Validates binary executability and version
- Provides paths for command execution
- Allows manual configuration of binary locations

**Detection Strategy:**
1. Check bundled binaries (if we include them)
2. Search system PATH
3. Check common installation locations
4. Allow user to specify custom paths

**Status:** Skeleton implemented with TODO for detection logic

### 5. FastpExecutor.java
**Purpose:** Executes fastp commands and manages process I/O.

**Responsibilities:**
- Builds command lines from user options
- Executes external fastp/fastplong processes
- Captures stdout/stderr output
- Reports progress to Geneious
- Handles cancellation
- Returns exit codes for error handling

**Key Methods:**
- `executeSingleEnd()`: Run fastp on single-end reads
- `executePairedEnd()`: Run fastp on paired-end reads
- `buildSingleEndCommand()`: Construct command for single-end
- `buildPairedEndCommand()`: Construct command for paired-end
- `executeCommand()`: Execute and monitor process

**Status:** Skeleton implemented with TODO for process execution

## Build System

The project uses **Apache Ant** for building, which is the standard build system for Geneious plugins.

### Build File: build.xml

**Key Targets:**
- `clean`: Removes build artifacts
- `prepare`: Creates build directories
- `compile`: Compiles Java sources (target Java 11)
- `build`: Creates JAR file with compiled classes and plugin.properties
- `distribute`: Creates .gplugin file for distribution (copy of JAR with .gplugin extension)

**Classpath Configuration:**
The build.xml references the Geneious DevKit libraries:
- `GeneiousPublicAPI.jar`: Core Geneious API
- `jdom.jar`: XML processing
- `jebl.jar`: JEBL bioinformatics library
- `commons-io-*.jar`: Apache Commons IO utilities
- `commons-lang3-*.jar`: Apache Commons Lang utilities

**DevKit Location:**
Currently hardcoded to: `/Users/dho/Downloads/geneious-2025.2.2-devkit`

To customize for your environment, edit the `devkit` property in build.xml.

## Plugin Metadata: plugin.properties

Contains essential plugin information:
- `plugin-name`: Fully qualified main class name (com.biomatters.plugins.fastp.FastpPlugin)
- `short-plugin-name`: Short name used for file naming (FastpPlugin)
- `version`: Plugin version (1.0.0)
- `author`: Plugin author
- `description`: Brief description of functionality

## Building the Plugin

### Prerequisites
- Java Development Kit (JDK) 11 or later
- Apache Ant
- Geneious Prime DevKit (2025.2.2 or compatible)

### Build Commands

```bash
# Clean previous builds
ant clean

# Compile sources
ant compile

# Build JAR
ant build

# Create distributable .gplugin file
ant distribute
```

### Build Output

After running `ant distribute`, you'll find:
- `build/FastpPlugin.jar`: The compiled plugin JAR
- `build/FastpPlugin.gplugin`: The distributable plugin file

## Installing the Plugin in Geneious

1. Build the plugin: `ant distribute`
2. Locate the .gplugin file: `build/FastpPlugin.gplugin`
3. Install in Geneious:
   - **Option A:** Tools → Plugins → Install Plugin from File
   - **Option B:** Copy to Geneious plugins directory:
     - macOS: `~/Library/Application Support/Geneious Prime/plugins/`
     - Windows: `%APPDATA%\Geneious Prime\plugins\`
     - Linux: `~/.geneious_prime/plugins/`
4. Restart Geneious Prime

## Referencing Geneious API

The plugin references the Geneious API through JARs in the DevKit:

### Required JARs
- **GeneiousPublicAPI.jar**: Main API for plugin development
  - Located at: `${devkit}/examples/GeneiousFiles/lib/GeneiousPublicAPI.jar`
  - Contains all public API classes and interfaces
  - Key packages:
    - `com.biomatters.geneious.publicapi.plugin.*`: Plugin framework
    - `com.biomatters.geneious.publicapi.documents.*`: Document types
    - `com.biomatters.geneious.publicapi.implementations.*`: Utility implementations

- **jebl.jar**: JEBL (Java Evolutionary Biology Library)
  - Contains progress monitoring and other utilities
  - Package: `jebl.util.*`

- **jdom.jar**: XML parsing library
  - Used for reading/writing XML data

### API Documentation
Complete API documentation is available in:
- `${devkit}/api-javadoc/`: HTML Javadoc for all public APIs
- `${devkit}/PhobosPluginDevelopment.pdf`: Comprehensive plugin development guide

### Key API Classes Used

**Plugin Framework:**
- `GeneiousPlugin`: Base class for all plugins
- `DocumentOperation`: Base class for operations that process documents
- `Options`: Base class for creating configuration UIs
- `GeneiousActionOptions`: Configures how operations appear in menus

**Document Types:**
- `AnnotatedPluginDocument`: Wrapper for documents with metadata
- `PluginDocument`: Base document interface
- `SequenceDocument`: Interface for sequence data
- `NucleotideSequenceDocument`: Nucleotide sequences

**Progress Monitoring:**
- `ProgressListener` (from jebl.util): Interface for progress callbacks

**Exceptions:**
- `DocumentOperationException`: Exception thrown when operations fail

## Development Status

### Completed
- [x] Project directory structure
- [x] Build configuration (build.xml)
- [x] Plugin metadata (plugin.properties)
- [x] Skeleton classes with comprehensive documentation
- [x] TODO comments explaining implementation steps

### Next Steps (Implementation Phase)

1. **FastpOptions.java**
   - [ ] Add all option fields using Geneious Options API
   - [ ] Implement option validation
   - [ ] Add tooltips and help text
   - [ ] Create preset configurations

2. **FastpBinaryManager.java**
   - [ ] Implement binary detection for macOS
   - [ ] Implement binary detection for Windows
   - [ ] Implement binary detection for Linux
   - [ ] Add version checking
   - [ ] Handle user-specified paths

3. **FastpExecutor.java**
   - [ ] Implement process execution
   - [ ] Add output capture and parsing
   - [ ] Implement progress reporting
   - [ ] Add cancellation support
   - [ ] Handle error conditions

4. **FastpOperation.java**
   - [ ] Implement document validation
   - [ ] Add paired-end detection
   - [ ] Add long-read detection
   - [ ] Implement FASTQ export
   - [ ] Implement fastp execution
   - [ ] Parse JSON output
   - [ ] Import processed sequences
   - [ ] Import HTML report
   - [ ] Add temporary file cleanup

5. **Testing**
   - [ ] Unit tests for each class
   - [ ] Integration tests with sample FASTQ files
   - [ ] Test with different input types
   - [ ] Test error handling
   - [ ] Test on multiple platforms

6. **Documentation**
   - [ ] User documentation
   - [ ] Installation guide
   - [ ] Parameter reference
   - [ ] Example workflows

## Fastp/Fastplong Features to Implement

### Core Quality Control
- Quality profiling and filtering
- Adapter detection and trimming
- Per-read quality pruning
- Base correction in overlapped regions (PE)

### Filtering Options
- Length filtering (min/max)
- Quality filtering (phred score thresholds)
- N-base filtering
- Complexity filtering
- Duplication analysis

### Output
- Filtered FASTQ files
- JSON statistics report
- HTML visualization report
- Integration of reports into Geneious

### Advanced Features
- UMI (Unique Molecular Identifier) processing
- Overrepresentation analysis
- Custom adapter sequences
- Polymerase chain trimming
- Multi-threading support

## Resources

- **Fastp GitHub:** https://github.com/OpenGene/fastp
- **Fastplong GitHub:** https://github.com/OpenGene/fastplong
- **Geneious DevKit Documentation:** `${devkit}/PhobosPluginDevelopment.pdf`
- **Geneious API Javadoc:** `${devkit}/api-javadoc/`

## License

TODO: Add license information

## Author

David Ho

## Version History

- **1.0.0** (Current): Initial project structure and skeleton implementation
