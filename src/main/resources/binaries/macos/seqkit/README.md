# Seqkit Binary for macOS

This directory should contain the seqkit binary for macOS.

## Required File

The following file must be placed in this directory before building the plugin:

- **seqkit** - The seqkit executable binary for macOS (universal binary supporting both Intel and Apple Silicon)

## Download Instructions

### Option 1: Download from GitHub Releases (Recommended)

1. Visit: https://github.com/shenwei356/seqkit/releases
2. Download the latest macOS binary (e.g., seqkit_darwin_amd64.tar.gz or seqkit_darwin_arm64.tar.gz)
   - For Intel Macs: seqkit_darwin_amd64.tar.gz
   - For Apple Silicon Macs: seqkit_darwin_arm64.tar.gz
   - Or download the universal binary if available
3. Extract the archive:
   ```bash
   tar -xzf seqkit_darwin_amd64.tar.gz
   ```
4. Copy the binary to this directory:
   ```bash
   cp seqkit /path/to/plugin/src/main/resources/binaries/macos/seqkit/
   ```

### Option 2: Using wget/curl

```bash
# Set version (check latest at https://github.com/shenwei356/seqkit/releases)
VERSION="2.8.2"

# For Intel Macs (x86_64)
curl -L "https://github.com/shenwei356/seqkit/releases/download/v${VERSION}/seqkit_darwin_amd64.tar.gz" -o seqkit.tar.gz

# OR for Apple Silicon Macs (arm64)
curl -L "https://github.com/shenwei356/seqkit/releases/download/v${VERSION}/seqkit_darwin_arm64.tar.gz" -o seqkit.tar.gz

# Extract
tar -xzf seqkit.tar.gz

# Copy to target directory
TARGET_DIR="/path/to/plugin/src/main/resources/binaries/macos/seqkit"
cp seqkit "$TARGET_DIR/"
```

### Option 3: Create Universal Binary (Recommended for Distribution)

To support both Intel and Apple Silicon Macs with a single binary:

```bash
# Download both architectures
VERSION="2.8.2"
curl -L "https://github.com/shenwei356/seqkit/releases/download/v${VERSION}/seqkit_darwin_amd64.tar.gz" -o amd64.tar.gz
curl -L "https://github.com/shenwei356/seqkit/releases/download/v${VERSION}/seqkit_darwin_arm64.tar.gz" -o arm64.tar.gz

# Extract both
mkdir amd64 arm64
tar -xzf amd64.tar.gz -C amd64/
tar -xzf arm64.tar.gz -C arm64/

# Create universal binary using lipo
lipo -create amd64/seqkit arm64/seqkit -output seqkit

# Verify it's universal
lipo -info seqkit
# Should output: "Architectures in the fat file: seqkit are: x86_64 arm64"

# Copy to target directory
TARGET_DIR="/path/to/plugin/src/main/resources/binaries/macos/seqkit"
cp seqkit "$TARGET_DIR/"
```

## File Verification

After downloading, verify the file is present and executable:

```bash
ls -lh seqkit
```

Expected output:
```
-rwxr-xr-x  1 user  staff   ~15M  seqkit
```

Make sure the binary has executable permissions:

```bash
chmod +x seqkit
```

Test the binary:

```bash
./seqkit version
```

Expected output should show version information like:
```
seqkit v2.8.2
```

Check architecture support (if universal binary):

```bash
lipo -info seqkit
```

Expected output for universal binary:
```
Architectures in the fat file: seqkit are: x86_64 arm64
```

## Version Information

Recommended version: seqkit v2.8.0 or later

Seqkit is a cross-platform and ultrafast toolkit for FASTA/Q file manipulation.

## Features Used in This Plugin

The plugin uses seqkit for:
- Fast duplicate sequence detection
- Sequence statistics and quality analysis
- FASTA/FASTQ format conversion
- Sequence filtering and manipulation
- High-performance parallel processing

## Additional Notes

- Seqkit is a standalone binary with no external dependencies
- It's written in Go and provides excellent performance
- The binary size is approximately 15-20 MB
- Universal binaries work on both Intel and Apple Silicon Macs
- Single-architecture binaries are smaller but only work on their respective architecture

## License

Seqkit is released under the MIT License.

## References

- Seqkit homepage: https://github.com/shenwei356/seqkit
- Documentation: https://bioinf.shenwei.me/seqkit/
- Paper: Shen W, Le S, Li Y, Hu F (2016) SeqKit: A Cross-Platform and Ultrafast Toolkit for FASTA/Q File Manipulation. PLOS ONE 11(10): e0163962. https://doi.org/10.1371/journal.pone.0163962
