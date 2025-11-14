package com.biomatters.plugins.fastp;

import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.GeneiousPlugin;

/**
 * Main plugin class for Fastp/Fastplong integration with Geneious Prime.
 *
 * This plugin provides quality control and preprocessing capabilities for
 * high-throughput sequencing data using the fastp and fastplong tools.
 *
 * Fastp is a tool designed for fast all-in-one preprocessing for FASTQ files.
 * It provides quality profiling, adapter trimming, quality filtering, per-read
 * quality pruning, and other preprocessing functions.
 *
 * Fastplong is the long-read variant optimized for PacBio and Nanopore data.
 *
 * Features to be implemented:
 * - Quality control analysis and visualization
 * - Adapter trimming and removal
 * - Quality filtering based on various metrics
 * - Read length filtering
 * - Complexity filtering
 * - HTML report generation
 * - Support for both paired-end and single-end reads
 * - Support for long-read technologies (PacBio, Nanopore)
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpPlugin extends GeneiousPlugin {

    /**
     * Returns the name of the plugin as it will appear in Geneious.
     *
     * @return the plugin name
     */
    @Override
    public String getName() {
        return "Fastp Quality Control";
    }

    /**
     * Returns a detailed description of the plugin's functionality.
     *
     * @return the plugin description
     */
    @Override
    public String getDescription() {
        return "Quality control and preprocessing for high-throughput sequencing data using fastp/fastplong. " +
               "Provides comprehensive QC analysis, adapter trimming, quality filtering, and preprocessing " +
               "for both short-read (Illumina) and long-read (PacBio, Nanopore) sequencing platforms.";
    }

    /**
     * Returns help text or documentation URL for the plugin.
     *
     * @return help text or null if not available
     */
    @Override
    public String getHelp() {
        return "This plugin integrates the fastp tool for quality control of sequencing reads. " +
               "Select one or more FASTQ files and run the Fastp operation to perform QC analysis, " +
               "filtering, and trimming. Results include processed sequences and a detailed HTML report.";
    }

    /**
     * Returns the author(s) of the plugin.
     *
     * @return author information
     */
    @Override
    public String getAuthors() {
        return "David Ho";
    }

    /**
     * Returns the current version of the plugin.
     *
     * @return version string
     */
    @Override
    public String getVersion() {
        return "1.0.0";
    }

    /**
     * Returns the minimum API version required for this plugin.
     * Geneious API version 4.0+ is required.
     *
     * @return minimum API version
     */
    @Override
    public String getMinimumApiVersion() {
        return "4.0";
    }

    /**
     * Returns the maximum API version this plugin supports.
     *
     * @return maximum API version
     */
    @Override
    public int getMaximumApiVersion() {
        return 4;
    }

    /**
     * Returns the array of document operations provided by this plugin.
     *
     * This is where we register all the operations that users can perform
     * with this plugin. Currently includes the main fastp operation.
     *
     * @return array of DocumentOperation instances
     */
    @Override
    public DocumentOperation[] getDocumentOperations() {
        return new DocumentOperation[]{
            new FastpOperation()
        };
    }

    // TODO: Future enhancements
    // - Add support for batch processing
    // - Integration with Geneious sequence lists
    // - Custom presets for different sequencing platforms
    // - Support for UMI (Unique Molecular Identifiers) processing
    // - Integration with downstream analysis pipelines
}
