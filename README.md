# Fastp Plugin for Geneious Prime

A comprehensive FASTQ preprocessing plugin for Geneious Prime that integrates quality control and read optimization tools into your sequence analysis workflow.

## Overview

The Fastp Plugin provides seamless integration of industry-standard preprocessing tools directly within Geneious Prime. It automatically handles both short and long reads, preserves quality scores throughout the workflow, and optimizes read files for better compression and downstream processing.

**Current Version:** 1.1.0

## Features

### Quality Control
- **Automatic read type detection** - Uses fastp for short reads and fastplong for long reads
- **Comprehensive HTML reports** - Detailed quality metrics and statistics
- **JSON output** - Machine-readable results for automation
- **Paired-end support** - Processes R1/R2 files with proper pairing

### Read Optimization
- **BBTools clumpify integration** - Sorts reads for 2-4x better compression
- **Multiple sorting modes:**
  - Optical duplicate detection and removal
  - Exact duplicate detection and removal
  - Spany mode for improved compression
  - Configurable compaction levels
  - Custom parameters for advanced users

### Quality Score Preservation
- **FASTQ format maintained** - Quality scores preserved through entire workflow
- **Paired-end integrity** - R1/R2 synchronization maintained
- **No data loss** - All sequence information retained

### User Interface
- **Tools menu integration** - Access via "Fastp QC" command
- **Advanced options panel** - Full control over all parameters
- **Command logging** - All executed commands logged to Info tab
- **Progress tracking** - Real-time status updates during processing

## Installation

### Prerequisites
- Geneious Prime (version 2023.0 or later recommended)
- Java 11 or later
- macOS (Intel or Apple Silicon)

### Installing the Plugin

1. Download the latest release (`FastpPlugin.gplugin`)
2. In Geneious Prime:
   - Go to **Tools → Plugins...**
   - Click **Install plugin from a gplugin file**
   - Select the downloaded `.gplugin` file
   - Restart Geneious Prime

Alternatively, copy the `.gplugin` file to:
- macOS: `~/Library/Application Support/Geneious Prime/plugins/`
- Windows: `%APPDATA%\Geneious Prime\plugins\`
- Linux: `~/.geneious_prime/plugins/`

### Verification

After installation, verify the plugin is loaded:
- Check **Tools → Plugins...** and look for "Fastp Plugin"
- The "Fastp QC" option should appear in the **Tools** menu

## Usage

### Basic Workflow

1. **Select sequences** - Select one or more FASTQ documents in Geneious
2. **Launch tool** - Go to **Tools → Fastp QC**
3. **Configure options** - Adjust quality control and clumpify settings
4. **Run analysis** - Click OK to process

### Paired-End Processing

The plugin automatically detects paired-end reads:
- Files are grouped by naming patterns (R1/R2)
- Both files receive identical quality control settings
- Pairing is maintained throughout the workflow
- Results are imported as paired-end sequence lists

### Quality Control Options

Configure fastp/fastplong parameters:
- **Quality filtering** - Minimum quality scores and window sizes
- **Length filtering** - Minimum and maximum read lengths
- **Adapter trimming** - Automatic adapter detection and removal
- **Complexity filtering** - Remove low-complexity sequences
- **Base correction** - Quality score recalibration

### Clumpify Options

Optimize read ordering for better compression:

**Enable Clumpify** - Toggle read optimization (enabled by default)

**Optical Duplicates:**
- Detects and removes optical duplicates
- Recommended for NovaSeq and other patterned flowcells

**Exact Duplicates:**
- Removes reads that are 100% identical
- Reduces file size and computational load

**Spany Mode:**
- Enables spany mode for paired-end reads
- Improved compression for paired data

**Compaction Level:**
- Default: Balanced compression and speed
- Conservative: Lower memory usage
- Aggressive: Maximum compression

**Custom Parameters:**
- Free-form field for advanced clumpify options
- Example: `groups=4 k=31`

### Output

The plugin generates:
- **Processed FASTQ files** - Quality-controlled and optimized reads
- **HTML reports** - Visual quality metrics and statistics
- **JSON reports** - Structured data for downstream analysis
- **Metadata** - All commands logged to document Info tab

## Building from Source

### Build Requirements
- JDK 11 or later
- Apache Ant 1.10+
- Geneious Prime SDK (2025.2.2 or compatible)

### Build Commands

**Full distribution (33MB - includes all binaries):**
```bash
ant clean
ant distribute
```

**Skinny distribution (66KB - no binaries):**
```bash
ant clean
ant distribute-skinny
```

### Build Output

**Full Version:**
- `build/FastpPlugin.gplugin` (33MB)
- Includes fastp, fastplong, BBTools suite (271+ utilities), and seqkit
- Ready to use immediately after installation

**Skinny Version:**
- `build/FastpPlugin-skinny.gplugin` (66KB)
- Plugin code only
- Requires separate installation of fastp, fastplong, BBTools, and seqkit
- Tools must be available in system PATH

## Included Tools

### fastp / fastplong
- **Size:** 3.2MB (macOS universal binary)
- **License:** MIT
- **Purpose:** Quality control for short and long reads
- **Source:** https://github.com/OpenGene/fastp

### BBTools Suite
- **Size:** 43MB
- **License:** BSD 3-Clause
- **Tools:** 271+ utilities (currently using clumpify)
- **Purpose:** Read optimization and future processing features
- **Source:** https://sourceforge.net/projects/bbmap/

### seqkit
- **Size:** 36MB
- **License:** MIT
- **Purpose:** Reserved for future sequence manipulation features
- **Source:** https://github.com/shenwei356/seqkit

All binaries are macOS universal builds supporting both Intel and Apple Silicon.

## Platform Support

### Currently Supported
- **macOS** (10.15 Catalina and later)
  - Intel (x86_64)
  - Apple Silicon (ARM64)
  - Universal binaries included

### Planned
- **Linux** (x86_64 and ARM64)
  - Binaries ready, integration planned for future release

## Workflow Pipeline

```
Input FASTQ Files
       ↓
   Export to temp directory
       ↓
   fastp/fastplong Quality Control
       ↓
   clumpify Read Optimization (optional)
       ↓
   Import to Geneious
       ↓
   Output: Processed FASTQ + Reports
```

## Version History

### 1.1.0 (Current)
- Added BBTools clumpify integration
- Command logging to Info tab
- Advanced clumpify options panel
- Quality score preservation improvements
- Full paired-end R1/R2 support

### 1.0.0
- Initial release
- fastp/fastplong integration
- Basic quality control features
- macOS support

## Troubleshooting

### Common Issues

**Plugin doesn't appear in Tools menu:**
- Verify installation in Tools → Plugins
- Restart Geneious Prime completely
- Check Java version (must be 11+)
- Try reinstalling the plugin

**Clumpify not running:**
- Check "Enable clumpify" checkbox in options
- Review command log in document Info tab
- Verify BBTools files were extracted (check console output)

**Paired-end files not detected:**
- Ensure file names follow R1/R2 naming convention
- Select both files before running operation
- Verify files are FASTQ format

**Out of memory errors:**
- Increase Geneious Prime memory allocation
- Process files in smaller batches
- Use lower clumpify compaction level

### Debug Information

All commands are logged to the document Info tab:
1. Select the processed document
2. Click the Info tab
3. Look for "Fastp Command" and "Clumpify Command" fields
4. Review command output for error messages

## Future Enhancements

Planned features for future releases:
- Additional BBTools utilities (bbduk, bbmerge, bbmap)
- seqkit integration for sequence manipulation
- Linux binary support
- Windows support
- Batch processing improvements
- Preset configurations

## Contributing

Contributions are welcome! Areas for improvement:
- Platform support (Linux, Windows)
- Additional BBTools integration
- UI enhancements
- Documentation improvements

## License

This plugin code is distributed under the BSD 3-Clause License.

### Third-Party Licenses

All included binaries are redistributable under permissive licenses:
- **fastp/fastplong:** MIT License
- **BBTools:** BSD 3-Clause License (U.S. Government work)
- **seqkit:** MIT License

Complete license texts available in `THIRD_PARTY_LICENSES.txt`

## Credits

### Plugin Development
- Architecture and implementation
- Workflow design
- Build system and distribution

### Third-Party Tools
- **fastp/fastplong** - Shifu Chen, OpenGene
- **BBTools** - Brian Bushnell, Joint Genome Institute, Lawrence Berkeley National Laboratory
- **seqkit** - Wei Shen, Oxford Nanopore Technologies

## Support

For issues, questions, or contributions:
- Submit GitHub issues with detailed error information
- Include Geneious version, plugin version, and macOS version
- Attach command log output from Info tab
- Provide sample data if possible (anonymized)

---

**This plugin is actively maintained and in production use.**
