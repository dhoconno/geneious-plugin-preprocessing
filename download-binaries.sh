#!/bin/bash

#
# Binary Download Script for Geneious Preprocessing Plugin
#
# This script downloads and sets up the required binaries:
# - BBTools suite (bbmap.jar, clumpify.sh, reformat.sh)
# - seqkit (universal binary for macOS)
#
# Usage: ./download-binaries.sh
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BBTOOLS_DIR="${SCRIPT_DIR}/src/main/resources/binaries/macos/bbtools"
SEQKIT_DIR="${SCRIPT_DIR}/src/main/resources/binaries/macos/seqkit"
TEMP_DIR="/tmp/plugin-binaries-$$"

# Versions (update these as needed)
BBTOOLS_VERSION="39.06"
SEQKIT_VERSION="2.8.2"

echo "=========================================="
echo "Binary Download Script"
echo "=========================================="
echo ""

# Create temp directory
mkdir -p "$TEMP_DIR"
echo -e "${GREEN}Created temporary directory: ${TEMP_DIR}${NC}"

# Cleanup function
cleanup() {
    echo -e "${YELLOW}Cleaning up temporary files...${NC}"
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

#
# Download and setup BBTools
#
echo ""
echo "=========================================="
echo "1. Downloading BBTools Suite"
echo "=========================================="

if [ -f "${BBTOOLS_DIR}/bbmap.jar" ] && [ -f "${BBTOOLS_DIR}/clumpify.sh" ] && [ -f "${BBTOOLS_DIR}/reformat.sh" ]; then
    echo -e "${YELLOW}BBTools files already exist. Skipping download.${NC}"
    echo "To re-download, delete files in: ${BBTOOLS_DIR}"
else
    echo "Downloading BBTools ${BBTOOLS_VERSION}..."

    # Download BBTools
    cd "$TEMP_DIR"
    curl -L "https://sourceforge.net/projects/bbmap/files/BBMap_${BBTOOLS_VERSION}.tar.gz/download" -o BBMap.tar.gz

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Download successful${NC}"

        # Extract
        echo "Extracting BBTools..."
        tar -xzf BBMap.tar.gz

        # Copy required files
        echo "Copying BBTools files..."
        mkdir -p "$BBTOOLS_DIR"
        cp bbmap/current/bbmap.jar "$BBTOOLS_DIR/"
        cp bbmap/clumpify.sh "$BBTOOLS_DIR/"
        cp bbmap/reformat.sh "$BBTOOLS_DIR/"

        # Make scripts executable
        chmod +x "$BBTOOLS_DIR/clumpify.sh"
        chmod +x "$BBTOOLS_DIR/reformat.sh"

        echo -e "${GREEN}BBTools setup complete!${NC}"
        ls -lh "$BBTOOLS_DIR"
    else
        echo -e "${RED}Failed to download BBTools${NC}"
        echo "Please download manually from: https://sourceforge.net/projects/bbmap/"
        exit 1
    fi
fi

#
# Download and setup seqkit
#
echo ""
echo "=========================================="
echo "2. Downloading seqkit"
echo "=========================================="

if [ -f "${SEQKIT_DIR}/seqkit" ]; then
    echo -e "${YELLOW}seqkit binary already exists. Skipping download.${NC}"
    echo "To re-download, delete file: ${SEQKIT_DIR}/seqkit"
else
    echo "Downloading seqkit ${SEQKIT_VERSION}..."
    echo "Creating universal binary for both Intel and Apple Silicon..."

    cd "$TEMP_DIR"

    # Download both architectures
    echo "Downloading AMD64 version..."
    curl -L "https://github.com/shenwei356/seqkit/releases/download/v${SEQKIT_VERSION}/seqkit_darwin_amd64.tar.gz" -o seqkit_amd64.tar.gz

    echo "Downloading ARM64 version..."
    curl -L "https://github.com/shenwei356/seqkit/releases/download/v${SEQKIT_VERSION}/seqkit_darwin_arm64.tar.gz" -o seqkit_arm64.tar.gz

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Downloads successful${NC}"

        # Extract both
        echo "Extracting binaries..."
        mkdir -p amd64 arm64
        tar -xzf seqkit_amd64.tar.gz -C amd64/
        tar -xzf seqkit_arm64.tar.gz -C arm64/

        # Create universal binary
        echo "Creating universal binary..."
        lipo -create amd64/seqkit arm64/seqkit -output seqkit_universal

        # Verify
        echo "Verifying universal binary..."
        lipo -info seqkit_universal

        # Copy to target directory
        mkdir -p "$SEQKIT_DIR"
        cp seqkit_universal "$SEQKIT_DIR/seqkit"
        chmod +x "$SEQKIT_DIR/seqkit"

        echo -e "${GREEN}seqkit setup complete!${NC}"
        ls -lh "$SEQKIT_DIR/seqkit"

        # Test version
        echo "Testing seqkit..."
        "$SEQKIT_DIR/seqkit" version
    else
        echo -e "${RED}Failed to download seqkit${NC}"
        echo "Please download manually from: https://github.com/shenwei356/seqkit/releases"
        exit 1
    fi
fi

#
# Verification
#
echo ""
echo "=========================================="
echo "3. Verification"
echo "=========================================="

echo ""
echo "Checking BBTools files..."
if [ -f "${BBTOOLS_DIR}/bbmap.jar" ]; then
    echo -e "${GREEN}✓${NC} bbmap.jar: $(du -h "${BBTOOLS_DIR}/bbmap.jar" | cut -f1)"
else
    echo -e "${RED}✗${NC} bbmap.jar: MISSING"
fi

if [ -f "${BBTOOLS_DIR}/clumpify.sh" ] && [ -x "${BBTOOLS_DIR}/clumpify.sh" ]; then
    echo -e "${GREEN}✓${NC} clumpify.sh: Present and executable"
else
    echo -e "${RED}✗${NC} clumpify.sh: MISSING or not executable"
fi

if [ -f "${BBTOOLS_DIR}/reformat.sh" ] && [ -x "${BBTOOLS_DIR}/reformat.sh" ]; then
    echo -e "${GREEN}✓${NC} reformat.sh: Present and executable"
else
    echo -e "${RED}✗${NC} reformat.sh: MISSING or not executable"
fi

echo ""
echo "Checking seqkit binary..."
if [ -f "${SEQKIT_DIR}/seqkit" ] && [ -x "${SEQKIT_DIR}/seqkit" ]; then
    echo -e "${GREEN}✓${NC} seqkit: $(du -h "${SEQKIT_DIR}/seqkit" | cut -f1)"

    # Check if universal
    ARCH_INFO=$(lipo -info "${SEQKIT_DIR}/seqkit" 2>&1)
    if [[ $ARCH_INFO == *"x86_64"* ]] && [[ $ARCH_INFO == *"arm64"* ]]; then
        echo -e "${GREEN}✓${NC} Universal binary (Intel + Apple Silicon)"
    else
        echo -e "${YELLOW}⚠${NC} Single architecture binary"
    fi
else
    echo -e "${RED}✗${NC} seqkit: MISSING or not executable"
fi

#
# Summary
#
echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "All required binaries have been downloaded and configured."
echo "You can now build the plugin using:"
echo "  ant clean distribute"
echo ""
echo "Binary locations:"
echo "  BBTools: ${BBTOOLS_DIR}"
echo "  seqkit:  ${SEQKIT_DIR}"
echo ""
