// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.vespaclient.ClusterList;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The vespa-get tool retrieves documents from a Vespa Document Storage cluster, and prints them to stdout as XML.
 *
 * @author bjorncs
 */
public class Main {

    public static void main(String[] args) {
        try {
            Logger.getLogger("").setLevel(Level.WARNING);
            CommandLineOptions options = new CommandLineOptions();
            ClientParameters params = options.parseCommandLineArguments(args);

            if (params.help) {
                options.printHelp();
            } else {
                DocumentRetriever documentRetriever = createDocumentRetriever(params);
                addShutdownHook(documentRetriever);
                documentRetriever.retrieveDocuments();
            }
        } catch (IllegalArgumentException e) {
            System.err.printf("Failed to parse command line arguments: %s.\n", e.getMessage());
        } catch (DocumentRetrieverException e) {
            System.err.printf("Failed to retrieve documents: %s\n", e.getMessage());
        }
    }

    private static void addShutdownHook(DocumentRetriever documentRetriever) {
        Runtime.getRuntime().addShutdownHook(new Thread(documentRetriever::shutdown));
    }

    private static DocumentRetriever createDocumentRetriever(ClientParameters params) {
        return new DocumentRetriever(
                new ClusterList("client"),
                new DocumentAccessFactory(),
                params
        );
    }
}
