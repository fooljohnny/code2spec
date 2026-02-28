package io.github.code2spec;

import io.github.code2spec.llm.LlmConfig;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * CLI entry point.
 */
public class CliApp {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Code2SpecCommand()).execute(args);
        System.exit(exitCode);
    }
}
