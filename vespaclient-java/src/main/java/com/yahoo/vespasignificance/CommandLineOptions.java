// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;

import ai.vespa.vespasignificance.export.ExportClientParameters;
import ai.vespa.vespasignificance.merge.MergeClientParameters;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;


import java.util.Comparator;
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
    public static final String OUTPUT_OPTION = "output";
    public static final String FIELD_OPTION = "field";
    public static final String LANGUAGE_OPTION = "language";
    public static final String ZST_COMPRESSION = "zst-compression";

    public static final String INDEX_DIR = "index-dir";
    public static final String CLUSTER_OPTION = "cluster";
    public static final String SCHEMA_NAME = "schema";
    public static final String NODE_INDEX_OPTION = "node-index";

    public static final String MIN_KEEP_OPTION = "min-keep";

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
        commands.put("export", "Export terms and document frequency from a flushed index to TSV.");
        commands.put("merge", "Merge terms and document frequency from a multiple TSV files.");
        return commands;
    }

    /** Pretty print the global help */
    static void printGlobalHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);
        fmt.setOptionComparator(Comparator.comparing(Option::getLongOpt));

        Comparator<String> byName = String.CASE_INSENSITIVE_ORDER;
        StringBuilder header = new StringBuilder("Commands:\n");
        registeredCommands().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(byName))
                .forEach(e -> header.append(String.format("  %-12s %s%n", e.getKey(), e.getValue())));
        header.append("\nOptions:");

        fmt.printHelp("vespa-significance <command> [options]", header.toString(), createGlobalOptions(), "", false);
    }

    /** Options for generate command */
    static Options createGenerateOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt(HELP_OPTION)
                .desc("Show this help and exit.")
                .build());

        options.addOption(Option.builder("i")
                .longOpt(INPUT_OPTION)
                .required()
                .hasArg()
                .argName("file.jsonl")
                .desc("Input JSON Lines file. One Vespa document per line.")
                .build());

        options.addOption(Option.builder("o")
                .longOpt(OUTPUT_OPTION)
                .required()
                .hasArg()
                .argName("model.json[.zst]")
                .desc("Output model file.")
                .build());

        options.addOption(Option.builder("f")
                .longOpt(FIELD_OPTION)
                .required()
                .hasArg()
                .argName("fieldName")
                .desc("Document field to analyze.")
                .build());

        options.addOption(Option.builder("l")
                .longOpt(LANGUAGE_OPTION)
                .required()
                .hasArg()
                .argName("tag[,tag...]")
                .desc("ISO language tag(s), comma-separated (e.g., 'en', 'no', or 'en,no').")
                .build());

        options.addOption(Option.builder("zst")
                .longOpt(ZST_COMPRESSION)
                .hasArg()
                .argName("true|false")
                .desc("Use Zstandard compression (default: false). If true, --out must end with .zst.")
                .build());

        return options;
    }

    /** Petty print help for generate command */
    public static void printGenerateHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);
        fmt.setDescPadding(2);
        fmt.setOptionComparator(Comparator.comparing(Option::getLongOpt));
        String header = "Options:";
        fmt.printHelp("vespa-significance generate [options]", header, createGenerateOptions(), "", false);
    }

    /** Parse generate command options to ClientParameters */
    public static ClientParameters parseGenerateCommandLineArguments(CommandLine cl) {
        ClientParameters.Builder builder = new ClientParameters.Builder();

        builder.setHelp(cl.hasOption(HELP_OPTION));
        builder.setInputFile(cl.getOptionValue(INPUT_OPTION));
        builder.setOutputFile(cl.getOptionValue(OUTPUT_OPTION));
        builder.setField(cl.getOptionValue(FIELD_OPTION));
        builder.setLanguage(cl.getOptionValue(LANGUAGE_OPTION));
        builder.setZstCompression(cl.hasOption(ZST_COMPRESSION) ? cl.getOptionValue(ZST_COMPRESSION) : "false");

        return builder.build();
    }

    /** Options for export command */
    static Options createExportOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt(HELP_OPTION)
                .desc("Show this help and exit.")
                .build());

        options.addOption(Option.builder()
                .longOpt(INDEX_DIR)
                .hasArg()
                .argName("path/to/index")
                .desc("Path to index directory.")
                .build());

        options.addOption(Option.builder()
                .longOpt(OUTPUT_OPTION)
                .hasArg()
                .argName("FILE.tsv[.zst]")
                .desc("Output TSV file.")
                .build());

        options.addOption(Option.builder()
                .longOpt(FIELD_OPTION)
                .required()
                .hasArg()
                .argName("FIELD")
                .desc("Field to export.")
                .build());

        options.addOption(Option.builder()
                .longOpt(CLUSTER_OPTION)
                .hasArg()
                .argName("NAME")
                .desc("Cluster name.")
                .build());

        options.addOption(Option.builder()
                .longOpt(SCHEMA_NAME)
                .hasArg()
                .argName("NAME")
                .desc("Schema name (document type).")
                .build());

        options.addOption(Option.builder()
                .longOpt(NODE_INDEX_OPTION)
                .hasArg()
                .argName("NUMBER")
                .desc("Node index directory.")
                .build());

        options.addOption(Option.builder("zst")
                .longOpt(ZST_COMPRESSION)
                .desc("Use Zstandard compression.")
                .build());

        return options;
    }

    /** Petty print help for export command */
    public static void printExportHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);
        fmt.setDescPadding(2);
        fmt.setOptionComparator(Comparator.comparing(Option::getLongOpt));
        String header = "Options:";
        fmt.printHelp("vespa-significance export [options]", header, createExportOptions(), "", false);
    }

    /** Parse generate command options to ClientParameters */
    public static ExportClientParameters parseExportCommandLineArguments(CommandLine cl) {
        return ExportClientParameters.builder()
                .fieldName(cl.getOptionValue(FIELD_OPTION))
                .outputFile(cl.hasOption(OUTPUT_OPTION) ? cl.getOptionValue(OUTPUT_OPTION) : "term_df.tsv")
                .indexDir(cl.getOptionValue(INDEX_DIR))
                .clusterName(cl.getOptionValue(CLUSTER_OPTION))
                .schemaName(cl.getOptionValue(SCHEMA_NAME))
                .nodeIndex(cl.getOptionValue(NODE_INDEX_OPTION))
                .zstCompress(cl.hasOption(ZST_COMPRESSION))
                .build();
    }

    /** Options for merge command */
    static Options createMergeOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt(HELP_OPTION)
                .desc("Show this help and exit.")
                .build());

        options.addOption(Option.builder()
                .longOpt(OUTPUT_OPTION)
                .hasArg()
                .argName("FILE.tsv[.zst]")
                .desc("Output TSV file.")
                .build());

        options.addOption(Option.builder()
                .longOpt(MIN_KEEP_OPTION)
                .hasArg()
                .argName("NUMBER")
                .desc("Filter out documents with frequency lower than NUMBER.")
                .build());

        options.addOption(Option.builder("zst")
                .longOpt(ZST_COMPRESSION)
                .desc("Use Zstandard compression.")
                .build());

        return options;
    }

    /** Petty print help for export command */
    public static void printMergeHelp() {
        HelpFormatter fmt = new HelpFormatter();
        fmt.setWidth(100);
        fmt.setLeftPadding(2);
        fmt.setDescPadding(2);
        fmt.setOptionComparator(Comparator.comparing(Option::getLongOpt));
        String cmd =
                "vespa-significance merge [options] <input.tsv[.zst]> [<input2.tsv[.zst]> ...]";

        String header = "Options:";
        fmt.printHelp(cmd, header, createMergeOptions(), "", false);
    }

    /**
     * Parse generate command options to ClientParameters. Expects CommandLine parameter to have
     * input files in getArgList(). Use {@link org.apache.commons.cli.DefaultParser} with stopAtNonOption=true
     * to ensure this.
     */
    public static MergeClientParameters parseMergeCommandLineArguments(CommandLine cl) {
        return MergeClientParameters.builder()
                .outputFile(cl.hasOption(OUTPUT_OPTION) ? cl.getOptionValue(OUTPUT_OPTION) : "term_df.tsv")
                .zstCompress(cl.hasOption(ZST_COMPRESSION))
                .minKeep(cl.hasOption(MIN_KEEP_OPTION) ? Long.parseLong(cl.getOptionValue(MIN_KEEP_OPTION)) : Long.MIN_VALUE)
                .addInputFiles(cl.getArgList())
                .build();
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

