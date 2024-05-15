// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;

import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;


import java.io.InputStream;


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
    public static final String DOC_TYPE_OPTION = "doc-type";

    private final Options options = createOptions();

    @SuppressWarnings("AccessStaticViaInstance")
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .hasArg(false)
                .desc("Show this syntax page.")
                .longOpt(HELP_OPTION)
                .build());

        options.addOption(Option.builder("i")
                .hasArg(true)
                .desc("Input file")
                .longOpt(INPUT_OPTION)
                .build());

        options.addOption(Option.builder("i")
                .hasArg(true)
                .desc("Output file")
                .longOpt(OUTPUT_OPTION)
                .build());

        options.addOption(Option.builder("f")
                .hasArg(true)
                .desc("Field to analyze")
                .longOpt(FIELD_OPTION)
                .build());

        options.addOption(Option.builder("l")
                .hasArg(true)
                .desc("Language tag for output file")
                .longOpt(LANGUAGE_OPTION)
                .build());

        options.addOption(Option.builder("d")
                .hasArg(true)
                .desc("Document type identifier")
                .longOpt(DOC_TYPE_OPTION)
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
            builder.setDocType(cl.getOptionValue(DOC_TYPE_OPTION));

            return builder.build();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed to parse command line arguments: " + e.getMessage());
        }
    }
}

