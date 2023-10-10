// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import com.yahoo.document.predicate.Predicate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Parses a feed file containing documents in XML format. Its implementation is based on the following assumptions:
 *  1. Each document has single predicate field.
 *  2. The predicate is stored in a field named "boolean".
 *
 *  @author bjorncs
 */
public class VespaFeedParser {

    public static int parseDocuments(String feedFile, int maxDocuments, Consumer<Predicate> consumer) throws IOException {
        int documentCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(feedFile), 8 * 1024)) {
            reader.readLine();
            reader.readLine(); // Skip to start of first document
            String line = reader.readLine();
            while (!line.startsWith("</vespafeed>") && documentCount < maxDocuments) {
                while (!line.startsWith("<boolean>")) {
                    line = reader.readLine();
                }
                Predicate predicate = Predicate.fromString(extractBooleanExpression(line));
                consumer.accept(predicate);
                ++documentCount;
                while (!line.startsWith("<document") && !line.startsWith("</vespafeed>")) {
                    line = reader.readLine();
                }
            }
        }
        return documentCount;
    }

    private static String extractBooleanExpression(String line) {
        return line.substring(9, line.length() - 10);
    }

}
