// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;

import org.apache.commons.cli.*;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * This class is responsible for parsing the command line arguments and print the help page.
 *
 * @author MariusArhaug
 */
public class CommandLineOptions {

    public static final String HELP_OPTION = "help";
    public static final String INPUT_OPTION = "in";
    public static final String OUTPUT_OPTION = "out";
    public static final String FIELD_OPTION = "field";
    public static final String LANGUAGE_OPTION = "language";
    public static final String ZST_COMPRESSION = "zst-compression";

    /** Options for selecting subcommand */
    static Options createGlobalOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show available commands.")
                .build());

        return options;
    }

    /** Map command name to description */
    static Map<String, String> registeredCommands() {
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("generate", "Generate a significance model from a JSONL feed file.");
        return commands;
    }

    /** Pretty print the global help */
    static void printGlobalHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);

        StringBuilder header = new StringBuilder("Commands:\n");
        registeredCommands().forEach((name, desc) -> header.append(String.format("  %-12s %s%n", name, desc)));
        header.append("\nOptions:");

        fmt.printHelp("vespa-significance <command>", header.toString(), createGlobalOptions(), "", true);
    }

    /** Options for generate command */
    static Options createGenerateOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .hasArg(false)
                .desc("Show this syntax page.")
                .longOpt(HELP_OPTION)
                .build());

        options.addOption(Option.builder("i")
                .required()
                .hasArg(true)
                .desc("Input file")
                .longOpt(INPUT_OPTION)
                .build());

        options.addOption(Option.builder("o")
                .required()
                .hasArg(true)
                .desc("Output file")
                .longOpt(OUTPUT_OPTION)
                .build());

        options.addOption(Option.builder("f")
                .required()
                .hasArg(true)
                .desc("Field to analyze")
                .longOpt(FIELD_OPTION)
                .build());

        options.addOption(Option.builder("l")
                .required()
                .hasArg(true)
                .desc("Language tag for output file")
                .longOpt(LANGUAGE_OPTION)
                .build());

        options.addOption(Option.builder("zst")
                .hasArg(true)
                .desc("Use Zstandard compression")
                .longOpt(ZST_COMPRESSION)
                .build());

        return options;
    }

    /** Petty print help for generate command */
    public static void printGenerateHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);
        fmt.setDescPadding(2);
        String header = "Options:";
        fmt.printHelp("vespa-significance generate [options]", header, createGenerateOptions(), "", true);
    }

    /** Parse generate command options to ClientParameters */
    public static ClientParameters parseGenerateCommandLineArguments(CommandLine cl) {
        ClientParameters.Builder builder = new ClientParameters.Builder();

        builder.setHelp(cl.hasOption(HELP_OPTION));
        builder.setInputFile(cl.getOptionValue(INPUT_OPTION));
        builder.setOutputFile(cl.getOptionValue(OUTPUT_OPTION));
        builder.setField(cl.getOptionValue(FIELD_OPTION));
        builder.setLanguage(cl.getOptionValue(LANGUAGE_OPTION));
        builder.setZstCompression(cl.hasOption(ZST_COMPRESSION) ? cl.getOptionValue(ZST_COMPRESSION) : "true");

        return builder.build();
    }

    /**
     * Utils for parsing command line manually.
     * <p>
     * For instance when there are required options, and we want to check for --help.
     */
    static class Utils {
        static boolean hasHelpOption(String[] args) {
            for (var arg : args) {
                if (List.of("--help", "-h").contains(arg)) {
                    return true;
                }
            }
            return false;
        }
    }
}

