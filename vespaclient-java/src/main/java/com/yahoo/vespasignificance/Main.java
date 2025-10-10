// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import ai.vespa.vespasignificance.export.Export;
import ai.vespa.vespasignificance.merge.MergeCommand;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.util.Arrays;

/**
 * The vespa-significance tool generates significance models based on input feed files.
 *
 * @author MariusArhaug
 */
public class Main {

    public static void main(String[] args) {
        var parser = new DefaultParser();
        CommandLine global;
        try {
            global = parser.parse(CommandLineOptions.createGlobalOptions(), args, true);
        } catch (ParseException e) {
            System.err.println("Parsing failed. Reason: " + e.getMessage());
            CommandLineOptions.printGlobalHelp();
            System.exit(1);
            return;
        }

        String[] remaining = global.getArgs();
        if (remaining.length == 0 || global.hasOption("help")) {
            CommandLineOptions.printGlobalHelp();
            return;
        }

        String sub = remaining[0];
        String[] subArgs = Arrays.copyOfRange(remaining, 1, remaining.length);
        switch (sub) {
            case "generate":
                runGenerate(subArgs);
                break;

            case "export":
                runExport(subArgs);
                break;

            case "merge":
                runMerge(subArgs);
                break;

            default:
                System.err.println("Error: Unknown command `" + sub + "`");
                CommandLineOptions.printGlobalHelp();
                break;
        }
    }

    static void runGenerate(String[] commandLineArgs) {
        try {
            if (CommandLineOptions.Utils.hasHelpOption(commandLineArgs)) {
                CommandLineOptions.printGenerateHelp();
                return;
            }

            var commandLineParser = new DefaultParser();
            CommandLine commandLine = commandLineParser.parse(CommandLineOptions.createGenerateOptions(), commandLineArgs);

            ClientParameters params = CommandLineOptions.parseGenerateCommandLineArguments(commandLine);
            System.setProperty("vespa.replace_invalid_unicode", "true");
            SignificanceModelGenerator significanceModelGenerator = createSignificanceModelGenerator(params);
            significanceModelGenerator.generate();
        } catch (ParseException e) {
            System.err.printf("Error: %s.\n", e.getMessage());
            CommandLineOptions.printGenerateHelp();
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SignificanceModelGenerator createSignificanceModelGenerator(ClientParameters params) {
        return new SignificanceModelGenerator(params);
    }

    static void runExport(String[] commandLineArgs) {
        try {
            if (CommandLineOptions.Utils.hasHelpOption(commandLineArgs)) {
                CommandLineOptions.printExportHelp();
                return;
            }

            var commandLineParser = new DefaultParser();
            CommandLine commandLine = commandLineParser.parse(CommandLineOptions.createExportOptions(), commandLineArgs);
            System.setProperty("vespa.replace_invalid_unicode", "true");

            var clientParams = CommandLineOptions.parseExportCommandLineArguments(commandLine);
            Export export = new Export(clientParams);
            System.exit(export.run());
        } catch (ParseException e) {
            System.err.printf("Error: %s.\n", e.getMessage());
            CommandLineOptions.printExportHelp();
            System.exit(1);
        }
    }

    static void runMerge(String[] commandLineArgs) {
        try {
            if (CommandLineOptions.Utils.hasHelpOption(commandLineArgs)) {
                CommandLineOptions.printMergeHelp();
                return;
            }

            var commandLineParser = new DefaultParser();
            CommandLine commandLine = commandLineParser.parse(CommandLineOptions.createMergeOptions(), commandLineArgs, true);
            System.setProperty("vespa.replace_invalid_unicode", "true");

            var clientParams = CommandLineOptions.parseMergeCommandLineArguments(commandLine);
            MergeCommand merge = new MergeCommand(clientParams);
            System.exit(merge.run());
        } catch (ParseException e) {
            System.err.printf("Error: %s.\n", e.getMessage());
            CommandLineOptions.printMergeHelp();
            System.exit(1);
        }
    }
}
