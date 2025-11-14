# BBTools Binaries for macOS

This directory should contain the BBTools suite binaries for macOS.

## Required Files

The following files must be placed in this directory before building the plugin:

1. **bbmap.jar** - The main BBTools Java archive
2. **clumpify.sh** - Shell script for duplicate/containment removal
3. **reformat.sh** - Shell script for file format conversion and manipulation

## Download Instructions

### Option 1: Direct Download from SourceForge

1. Visit: https://sourceforge.net/projects/bbmap/
2. Download the latest version (e.g., BBMap_39.06.tar.gz)
3. Extract the archive:
   ```bash
   tar -xzf BBMap_39.06.tar.gz
   ```
4. Copy the required files to this directory:
   ```bash
   cd bbmap/
   cp current/bbmap.jar /path/to/plugin/src/main/resources/binaries/macos/bbtools/
   cp clumpify.sh /path/to/plugin/src/main/resources/binaries/macos/bbtools/
   cp reformat.sh /path/to/plugin/src/main/resources/binaries/macos/bbtools/
   ```

### Option 2: Using wget/curl

```bash
# Download BBTools
cd /tmp
wget https://sourceforge.net/projects/bbmap/files/latest/download -O BBMap.tar.gz

# Extract
tar -xzf BBMap.tar.gz

# Copy files
TARGET_DIR="/path/to/plugin/src/main/resources/binaries/macos/bbtools"
cp bbmap/current/bbmap.jar "$TARGET_DIR/"
cp bbmap/clumpify.sh "$TARGET_DIR/"
cp bbmap/reformat.sh "$TARGET_DIR/"
```

## File Verification

After downloading, verify the files are present:

```bash
ls -lh
```

Expected output:
```
-rw-r--r--  1 user  staff   ~10M  bbmap.jar
-rwxr-xr-x  1 user  staff   ~2K   clumpify.sh
-rwxr-xr-x  1 user  staff   ~2K   reformat.sh
```

Make sure the shell scripts have executable permissions:

```bash
chmod +x clumpify.sh reformat.sh
```

## Version Information

Recommended version: BBMap 39.06 or later

BBTools is maintained by Brian Bushnell at JGI (Joint Genome Institute).

## Additional Notes

- BBTools is Java-based and requires Java 8 or later to run
- The jar file contains all necessary dependencies
- The shell scripts are wrappers that call the Java classes with appropriate memory settings
- BBTools works on both Intel (x86_64) and Apple Silicon (arm64) Macs since it's Java-based

## License

BBTools is released under a modified BSD license. See the BBTools documentation for details.

## References

- BBTools homepage: https://jgi.doe.gov/data-and-tools/software-tools/bbtools/
- SourceForge project: https://sourceforge.net/projects/bbmap/
- User guide: https://jgi.doe.gov/data-and-tools/software-tools/bbtools/bb-tools-user-guide/
