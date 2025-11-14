package com.biomatters.plugins.fastp;

/**
 * Holds the result of a fastp/fastplong execution including the command,
 * exit code, and captured output streams.
 *
 * @author David Ho
 * @version 1.0.0
 */
public class FastpExecutionResult {

    private final String command;
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    /**
     * Creates a new execution result.
     *
     * @param command the full command that was executed
     * @param exitCode the process exit code
     * @param stdout the captured standard output
     * @param stderr the captured standard error
     */
    public FastpExecutionResult(String command, int exitCode, String stdout, String stderr) {
        this.command = command != null ? command : "";
        this.exitCode = exitCode;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
    }

    /**
     * Gets the command that was executed.
     *
     * @return the full command line
     */
    public String getCommand() {
        return command;
    }

    /**
     * Gets the exit code.
     *
     * @return the process exit code (0 = success)
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Gets the standard output.
     *
     * @return captured stdout
     */
    public String getStdout() {
        return stdout;
    }

    /**
     * Gets the standard error output.
     *
     * @return captured stderr
     */
    public String getStderr() {
        return stderr;
    }

    /**
     * Checks if the execution was successful.
     *
     * @return true if exit code is 0
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    @Override
    public String toString() {
        return "FastpExecutionResult{" +
               "command='" + command + '\'' +
               ", exitCode=" + exitCode +
               ", stdoutLength=" + stdout.length() +
               ", stderrLength=" + stderr.length() +
               '}';
    }
}
