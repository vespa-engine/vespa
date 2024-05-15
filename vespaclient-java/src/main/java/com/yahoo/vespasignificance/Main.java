// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import java.io.IOException;
import java.util.List;

/**
 * The vespa-significance tool generates significance models based on input feed files.
 *
 * @author MariusArhaug
 */

public class Main {

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.err.println("No arguments provided. Use --help to see available options.");
                System.exit(1);
            }

            if (!args[0].equals("generate")) {
                System.err.println("Invalid command. Use 'generate' to generate significance models.");
                System.exit(1);
            }
            String[] commandLineArgs = List.of(args).subList(1, args.length).toArray(new String[0]);

            CommandLineOptions options = new CommandLineOptions();
            ClientParameters params = options.parseCommandLineArguments(commandLineArgs);

            if (params.help) {
                options.printHelp();
            } else {
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
