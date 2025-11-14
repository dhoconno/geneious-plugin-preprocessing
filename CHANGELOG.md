# Changelog

All notable changes to the Fastp Plugin for Geneious Prime will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-11-14

### Added
- **Command Logging to Info Tab**: The exact fastp/fastplong command executed is now saved to the Info tab of output sequence lists
- **Stdout Logging to Info Tab**: Standard output from fastp/fastplong is captured and saved to the Info tab
- **Stderr Logging to Info Tab**: Standard error output from fastp/fastplong is captured and saved to the Info tab
- **Exit Code Logging**: Process exit code is now logged to the Info tab
- **Enhanced Console Logging**: Comprehensive logging to terminal for debugging when launching Geneious from command line
- **FastpExecutionResult Class**: New result class to encapsulate command, exit code, stdout, and stderr

### Changed
- **FastpExecutor**: Refactored to return `FastpExecutionResult` instead of just exit code
- **Sequence List Import**: Fixed to import as single sequence list instead of individual sequences
- **SequenceListDocument Support**: Added proper handling for SRA imports and other sequence list formats

### Fixed
- **SRA Import Support**: Plugin now properly handles sequence lists from SRA downloads
- **Document Type Detection**: Improved detection of SequenceListDocument vs individual sequences

## [1.0.0] - 2025-11-13

### Added
- Initial release of Fastp Plugin for Geneious Prime
- Integration of fastp v1.0.1 and fastplong v0.4.1
- Automatic paired-end detection via file naming conventions
- Automatic read type detection (short <1000bp vs long >=1000bp reads)
- Universal macOS binaries (Intel x86_64 + Apple Silicon arm64)
- 40+ configurable parameters with sensible defaults
- Quality filtering, adapter trimming, length filtering
- PolyG/PolyX trimming for NextSeq/NovaSeq platforms
- UMI processing support
- Comprehensive statistics in Geneious Info tab
- No external dependencies (binaries bundled)

### Implementation
- 5 Java classes (2,927 lines of production code)
- FastpPlugin: Plugin registration
- FastpOperation: Main workflow orchestration
- FastpOptions: UI with 40+ parameters
- FastpExecutor: Command execution
- FastpBinaryManager: Binary management

### License
- Plugin code: MIT License
- Bundled binaries: fastp and fastplong (both MIT licensed)

### Platform Support
- macOS: Full support (universal binary)
- Linux/Windows: Planned for future releases
