# Fastp Plugin for Geneious Prime - Installation and Usage Guide

## Overview

This plugin integrates **fastp** (short-read quality control) and **fastplong** (long-read quality control) into Geneious Prime, allowing you to perform comprehensive quality control, filtering, and preprocessing of FASTQ sequencing data directly within Geneious.

### Features

- ✓ Automatic detection of paired-end vs single-end reads
- ✓ Automatic detection of short-read vs long-read data
- ✓ Quality filtering and trimming
- ✓ Adapter detection and removal
- ✓ Length filtering
- ✓ Per-read quality cutting
- ✓ PolyG/PolyX tail trimming (for NextSeq/NovaSeq)
- ✓ Base correction in overlapped regions (paired-end)
- ✓ UMI processing
- ✓ Complexity filtering and deduplication
- ✓ Comprehensive statistics and HTML reports
- ✓ Integration with Geneious document metadata

### Version Information

- **Plugin Version**: 1.0.0
- **Fastp Version**: 1.0.1
- **Fastplong Version**: 0.4.1
- **Supported Platforms**: macOS (Intel and Apple Silicon)
- **Geneious Compatibility**: Geneious Prime 2025.2.2+

---

## Installation

### Step 1: Download the Plugin

The plugin file is located at:
```
/Users/dho/Documents/geneious-plugin-fastp/build/FastpPlugin.gplugin
```

### Step 2: Install in Geneious

**Method 1: Via Plugins Directory**
```bash
cp /Users/dho/Documents/geneious-plugin-fastp/build/FastpPlugin.gplugin \
   ~/Library/Application\ Support/Geneious\ Prime/plugins/
```

**Method 2: Via Geneious UI**
1. Open Geneious Prime
2. Go to **Tools → Plugins → Install Plugin from File...**
3. Navigate to and select `FastpPlugin.gplugin`
4. Click **Install**

### Step 3: Restart Geneious

Close and restart Geneious Prime to activate the plugin.

### Step 4: Verify Installation

After restart:
1. Go to **Tools → Plugins**
2. Verify "Fastp Quality Control Plugin" appears in the list
3. Check the toolbar - you should see "Run Fastp QC" button
4. Right-click on any sequence list - "Run Fastp QC" should appear in the context menu

---

## Usage

### Basic Workflow

1. **Import FASTQ Data**
   - Import your FASTQ files into Geneious
   - Files will be recognized as sequence lists

2. **Select Sequence Lists**
   - Select one or more sequence lists in the Document Table
   - For paired-end data, you can select individual files or both R1/R2

3. **Launch Fastp**
   - Click **"Run Fastp QC"** in the toolbar, or
   - Right-click and select **"Run Fastp QC"**, or
   - Go to **Tools → Run Fastp QC**

4. **Configure Options**
   - The options panel will appear with all available parameters
   - Configure as needed or use default presets

5. **Run**
   - Click **OK** to start processing
   - Progress will be shown in the progress dialog

6. **View Results**
   - Filtered sequence lists will appear in the Document Table
   - Click on output sequences to view metadata in the Info tab
   - Statistics include read counts, quality metrics, GC content, etc.

---

## Tool Selection

The plugin can automatically detect the appropriate tool based on your data:

### Auto-Detect Mode (Default)

The plugin analyzes the first 10 sequences to determine average read length:
- **Short reads** (< 1000 bp) → Uses **fastp**
- **Long reads** (≥ 1000 bp) → Uses **fastplong**

### Manual Selection

You can override auto-detection:
- **Tool Selection** dropdown:
  - `Auto-detect` (recommended)
  - `Fastp` (force short-read mode)
  - `Fastplong` (force long-read mode)

---

## Paired-End Detection

The plugin automatically detects paired-end data using naming conventions:

### Supported R1 Indicators
- `_R1`, `_1`, `.R1`, `.1`
- `_forward`, `_fwd`

### Supported R2 Indicators
- `_R2`, `_2`, `.R2`, `.2`
- `_reverse`, `_rev`

### Examples
- `sample_R1.fastq` and `sample_R2.fastq` → Detected as paired
- `sample_1.fq.gz` and `sample_2.fq.gz` → Detected as paired
- `sample.fastq` → Processed as single-end

### Manual Pairing

If your files don't follow standard naming:
1. Select both R1 and R2 files together
2. The plugin will process them as single-end unless names match
3. Consider renaming files to use standard conventions

---

## Options Guide

### Basic Options

**Number of Threads** (1-32, default: 4)
- More threads = faster processing
- Recommended: 4-8 for most systems

**Compression Level** (1-9, default: 4)
- Higher = smaller output files, slower processing
- Recommended: 4 for balance

---

### Quality Filtering

**Disable Quality Filtering**
- Check to skip all quality-based filtering

**Qualified Quality Phred** (0-40, default: 15)
- Minimum quality for a base to be considered "qualified"
- Phred 15 = 96.8% accuracy, Phred 20 = 99% accuracy

**Unqualified Percent Limit** (0-100%, default: 40)
- Maximum percentage of unqualified bases allowed per read
- Reads exceeding this are discarded

**N Base Limit** (0-100, default: 5)
- Maximum N (unknown) bases allowed per read

**Average Quality** (0-40, default: 0/disabled)
- Minimum average quality required for entire read
- 0 = disabled

---

### Length Filtering

**Disable Length Filtering**
- Check to skip length-based filtering

**Minimum Length** (0-10000, default: 15)
- Reads shorter than this are discarded
- Prevents very short reads from downstream analysis

**Maximum Length** (0-100000, default: 0/unlimited)
- Reads longer than this are discarded
- 0 = no maximum

---

### Adapter Trimming

**Disable Adapter Trimming**
- Check to skip adapter removal

**Auto-detect Adapters** (default: enabled)
- Automatically detects and removes common Illumina adapters
- Recommended for most uses

**Custom Adapter R1** / **R2**
- Specify custom adapter sequences if auto-detect doesn't work
- Enter sequences in ATGCN format
- R2 field only active for paired-end data

---

### Per-Read Quality Cutting

**Cut Front** (5' end cutting)
- Removes low-quality bases from beginning of reads

**Cut Tail** (3' end cutting)
- Removes low-quality bases from end of reads

**Cut Right** (alternative 3' algorithm)
- Alternative per-read cutting algorithm

**Cut Window Size** (1-100, default: 4)
- Sliding window size for quality cutting

**Cut Mean Quality** (0-40, default: 20)
- Mean quality threshold within window
- Windows below this quality are trimmed

---

### Base Trimming (Fixed Positions)

**Trim Front Bases (R1)** (0-100)
- Remove this many bases from 5' end of R1 reads

**Trim Tail Bases (R1)** (0-100)
- Remove this many bases from 3' end of R1 reads

**Trim Front Bases (R2)** (0-100)
- Remove this many bases from 5' end of R2 reads (paired-end only)

**Trim Tail Bases (R2)** (0-100)
- Remove this many bases from 3' end of R2 reads (paired-end only)

---

### PolyG/PolyX Trimming

Useful for Illumina NextSeq/NovaSeq (two-color chemistry).

**Enable PolyG Tail Trimming**
- Removes polyG tails caused by dark cycles
- Recommended for NextSeq/NovaSeq data

**PolyG Minimum Length** (1-100, default: 10)
- Minimum length of polyG tail to trigger trimming

**Enable PolyX Tail Trimming**
- Removes tails of any repeated base (AAAA, TTTT, etc.)

**PolyX Minimum Length** (1-100, default: 10)
- Minimum length of polyX tail to trigger trimming

---

### Paired-End Options

Only displayed when paired-end reads are detected.

**Base Correction in Overlapped Regions**
- Uses read overlap to correct sequencing errors
- Highly recommended for paired-end data with overlap

**Overlap Length Requirement** (1-1000, default: 30)
- Minimum overlap length required for correction

**Overlap Difference Limit** (0-100, default: 5)
- Maximum mismatches allowed in overlap region

---

### UMI Processing

For data with Unique Molecular Identifiers.

**Enable UMI Processing**
- Check to extract and process UMIs

**UMI Location**
- None / Index1 / Index2 / Read1 / Read2 / Per Index / Per Read

**UMI Length** (0-100)
- Length of UMI sequence in bases

**UMI Prefix**
- Prefix to add before UMI in read name

**UMI Skip Bases** (0-100)
- Bases to skip after UMI before sequence starts

---

### Advanced Filtering

**Enable Complexity Filter**
- Filters out low-complexity reads (e.g., AAAAA...)
- Useful for removing artifacts

**Complexity Threshold** (0-100%, default: 30)
- Percentage of different bases required

**Enable Deduplication**
- Removes exact duplicate reads
- Uses sequence-based matching

---

## Output and Results

### Output Documents

For each input sequence list, the plugin creates:
1. **Filtered sequence list** - Quality-controlled sequences
2. **Processing log** - Details of the fastp run (in console output)

### Metadata Fields

Each output sequence list includes these custom fields (visible in Info tab):

- **Reads Before** - Total reads in input
- **Reads After** - Total reads after filtering
- **Bases Before** - Total bases in input
- **Bases After** - Total bases after filtering
- **Q20 Rate** - Percentage of bases with quality ≥ 20
- **Q30 Rate** - Percentage of bases with quality ≥ 30
- **GC Content** - Percentage of G+C bases
- **Duplication Rate** - Percentage of duplicate reads
- **Original Document** - Reference to input file
- **Processing Date** - When fastp was run
- **Tool Used** - fastp or fastplong

### Sequence List Description

Output sequence lists have updated descriptions noting they've been processed through fastp/fastplong.

### HTML Reports

Fastp generates detailed HTML reports with:
- Quality score distributions
- Base content distributions
- Read length distributions
- Adapter content
- Overrepresented sequences
- Filtering statistics charts

*Note: HTML reports are currently logged to the console with their file paths. Future versions will import them as Geneious documents.*

---

## Common Use Cases

### RNA-Seq Quality Control

Recommended settings:
- Tool: Fastp (auto-detect)
- Quality: Qualified phred ≥ 20
- Length: Min 50 bp
- Adapter trimming: Auto-detect enabled
- PolyG trimming: Enable if using NextSeq/NovaSeq

### Whole Genome Sequencing (WGS)

Recommended settings:
- Tool: Fastp (auto-detect)
- Quality: Qualified phred ≥ 15
- Length: Min 30 bp
- Paired-end correction: Enable
- Deduplication: Enable

### PacBio/Nanopore Long Reads

Recommended settings:
- Tool: Fastplong (or auto-detect)
- Quality: Lower thresholds (reads are lower quality)
- Length: Min 500 bp, adjust based on your application
- Skip most short-read specific options

### Amplicon Sequencing

Recommended settings:
- Tool: Fastp
- Quality: Qualified phred ≥ 20
- Length: Match expected amplicon size ± tolerance
- Complexity filter: Enable
- Deduplication: Enable

---

## Troubleshooting

### Plugin doesn't appear in Geneious

**Solution:**
1. Verify plugin file is in correct directory:
   ```bash
   ls ~/Library/Application\ Support/Geneious\ Prime/plugins/
   ```
2. Ensure file has .gplugin extension
3. Restart Geneious completely
4. Check Tools → Plugins for error messages

### "Binary not found" or "Binary not executable" errors

**Solution:**
The binaries are bundled in the plugin JAR. This error indicates extraction failed.

1. Check temp directory permissions:
   ```bash
   ls -la /tmp/
   ```
2. Verify you have disk space
3. Check console for detailed error messages

### No output generated

**Possible causes:**
1. All reads were filtered out (too strict settings)
2. Input files are not valid FASTQ format
3. Process was cancelled

**Solution:**
1. Check console output for fastp messages
2. Try less strict filtering parameters
3. Verify input files are FASTQ format

### Paired-end not detected

**Solution:**
1. Verify file naming follows conventions (_R1/_R2 or _1/_2)
2. Rename files if needed
3. Process as single-end if pairing can't be determined

### Plugin runs slowly

**Solution:**
1. Increase thread count (up to number of CPU cores)
2. Disable unnecessary filtering options
3. Process smaller batches of files

---

## Performance Tips

### Optimize Thread Count

- Check your CPU core count: `sysctl -n hw.ncpu`
- Set threads to 50-75% of available cores
- Leave some cores for Geneious and OS

### Process Files in Batches

- Don't select too many sequence lists at once
- Process 10-20 files per batch for best performance

### Adjust Compression

- Lower compression (1-3) = faster, larger files
- Higher compression (7-9) = slower, smaller files
- Default (4) is good balance

### Monitor Resources

- Watch Activity Monitor during processing
- Fastp is CPU and I/O intensive
- Ensure adequate free disk space (2-3x input size)

---

## Known Limitations

### Platform Support

- Currently **macOS only** (Intel and Apple Silicon)
- Linux and Windows support coming in future versions

### HTML Report Import

- HTML reports are generated but not yet imported as Geneious documents
- Reports are saved to temp directory (path logged to console)
- Future versions will display reports in Geneious

### Quality Score Handling

- Plugin generates default quality scores (Phred 40) if input lacks them
- Some Geneious API quality methods may not be fully supported

---

## Support and Contact

### Getting Help

1. Check this documentation first
2. Review console output for error messages
3. Check Geneious log file: `~/Library/Application Support/Geneious Prime/geneious.log`

### Reporting Issues

When reporting issues, please include:
- Geneious Prime version
- Plugin version
- macOS version and architecture (Intel/Apple Silicon)
- Input file format and size
- Error messages from console
- Steps to reproduce

### Developer

David Ho
Email: [Your email]
GitHub: [Your repository URL]

---

## Version History

### Version 1.0.0 (Current)
- Initial release
- Fastp 1.0.1 / Fastplong 0.4.1
- macOS support (universal binary)
- Full parameter coverage
- Automatic paired-end detection
- Automatic read type detection
- Comprehensive metadata integration

### Planned Features
- Linux and Windows support
- HTML report import and display
- Parameter presets (RNA-seq, WGS, etc.)
- Batch processing improvements
- Advanced filtering options

---

## License

This plugin integrates:
- **Fastp**: MIT License (https://github.com/OpenGene/fastp)
- **Fastplong**: MIT License (https://github.com/OpenGene/fastplong)

Plugin code: [Your license]

---

## Acknowledgments

- Shifu Chen and team for fastp/fastplong
- Biomatters for Geneious Prime platform
- Geneious Plugin Developer Kit

---

## Quick Reference

### Installation
```bash
cp build/FastpPlugin.gplugin ~/Library/Application\ Support/Geneious\ Prime/plugins/
```

### Basic Usage
1. Select FASTQ sequence list(s)
2. Click "Run Fastp QC"
3. Configure options (or use defaults)
4. Click OK

### Key Settings for Common Tasks

**Quick QC (minimal filtering)**
- All filters: Default values
- Threads: 4-8

**Strict QC (maximum quality)**
- Qualified phred: 25
- Unqualified %: 30
- Min length: 50
- Enable deduplication

**Paired-end overlapping reads**
- Enable base correction
- Overlap length: 30
- Overlap diff limit: 5

---

**End of Documentation**
