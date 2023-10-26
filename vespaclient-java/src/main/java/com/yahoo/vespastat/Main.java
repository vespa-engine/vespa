// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class
 *
 * @author bjorncs
 */
public class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Logger.getLogger("").setLevel(Level.WARNING);
        CommandLineOptions options = new CommandLineOptions();
        try {
            ClientParameters params = options.parseCommandLineArguments(args);
            if (params.help) {
                options.printHelp();
                return;
            }
            BucketStatsRetriever retriever = new BucketStatsRetriever(
                    new DocumentAccessFactory(),
                    params.route,
                    createShutdownHookRegistrar());
            BucketStatsPrinter printer = new BucketStatsPrinter(retriever, System.out);
            printer.retrieveAndPrintBucketStats(params.selectionType, params.id, params.dumpData, params.bucketSpace);
        } catch (IllegalArgumentException e) {
            System.err.printf("Failed to parse command line arguments: %s.\n", e.getMessage());
        } catch (BucketStatsException e) {
            System.err.println(e.getMessage());
        }
    }

    private static BucketStatsRetriever.ShutdownHookRegistrar createShutdownHookRegistrar() {
        return runnable -> Runtime.getRuntime().addShutdownHook(new Thread(runnable));
    }

}
