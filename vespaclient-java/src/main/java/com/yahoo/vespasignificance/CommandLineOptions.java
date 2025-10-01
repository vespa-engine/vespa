// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;

import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;


import java.util.LinkedHashMap;
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

    private final Options options = createOptions();

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

    @SuppressWarnings("AccessStaticViaInstance")
    private static Options createOptions() {
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

    public void printHelp() {
        HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp(
                "vespa-significance <command> <options>", "Perform a significance value related operation.", options,
                "The generate command generates a significance model file for a given corpus type .jsonl file.\n",
                false);
    }

    public ClientParameters parseCommandLineArguments(String[] args) throws IllegalArgumentException {
        try {
            CommandLineParser clp = new DefaultParser();
            CommandLine cl = clp.parse(options, args);
            ClientParameters.Builder builder = new ClientParameters.Builder();

            builder.setHelp(cl.hasOption(HELP_OPTION));
            builder.setInputFile(cl.getOptionValue(INPUT_OPTION));
            builder.setOutputFile(cl.getOptionValue(OUTPUT_OPTION));
            builder.setField(cl.getOptionValue(FIELD_OPTION));
            builder.setLanguage(cl.getOptionValue(LANGUAGE_OPTION));
            builder.setZstCompression(cl.hasOption(ZST_COMPRESSION) ? cl.getOptionValue(ZST_COMPRESSION) : "true");

            return builder.build();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to parse command line arguments: " + e.getMessage());
        }
    }
}

