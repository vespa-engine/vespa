// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.util.Arrays;

import static com.yahoo.vespasignificance.CommandLineOptions.createGlobalOptions;
import static com.yahoo.vespasignificance.CommandLineOptions.printGlobalHelp;

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
            global = parser.parse(createGlobalOptions(), args, true);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        String[] remaining = global.getArgs();
        if (remaining.length == 0 || global.hasOption("help")) {
            printGlobalHelp();
            return;
        }


        String sub = remaining[0];
        String[] subArgs = Arrays.copyOfRange(remaining, 1, remaining.length);
        switch (sub) {
            case "generate":
                runGenerate(subArgs);
                break;

            default:
                System.err.println("Error: Unknown command `" + sub + "`");
                printGlobalHelp();
                break;
        }
    }

    static void runGenerate(String[] commandLineArgs) {
        try {
            CommandLineOptions options = new CommandLineOptions();
            ClientParameters params = options.parseCommandLineArguments(commandLineArgs);

            if (params.help) {
                options.printHelp();
            } else {
                System.setProperty("vespa.replace_invalid_unicode", "true");
                SignificanceModelGenerator significanceModelGenerator = createSignificanceModelGenerator(params);
                significanceModelGenerator.generate();
            }
        } catch (IllegalArgumentException e) {
            System.err.printf("Failed to parse command line arguments: %s.\n", e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SignificanceModelGenerator createSignificanceModelGenerator(ClientParameters params) {
        return new SignificanceModelGenerator(params);
    }
}
