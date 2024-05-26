// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import com.yahoo.document.predicate.Predicate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Parses a feed file containing documents in JSON format. Its implementation is based on the following assumptions:
 *  1. Each document has single predicate field.
 *  2. The predicate is stored in a field named "boolean".
 *  3. There is just one "boolean" field on each line
 *
 *  @author bjorncs
 */
public class VespaFeedParser {

    public static int parseDocuments(String feedFile, int maxDocuments, Consumer<Predicate> consumer) throws IOException {
        int documentCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(feedFile), 8 * 1024)) {
            String line = reader.readLine();
            while (!line.startsWith("]") && documentCount < maxDocuments) {
                while (!line.contains("\"boolean\":")) {
                    line = reader.readLine();
                }
                String booleanExpression = extractBooleanExpression(line);
                try {
                    var predicate = Predicate.fromString(booleanExpression);
                    consumer.accept(predicate);
                    ++documentCount;
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Failed to parse predicate: " + booleanExpression, e);
                }
                line = reader.readLine();
            }
        }
        return documentCount;
    }

    private static String extractBooleanExpression(String line) {
        String field = "\"boolean\":";
        var start = line.indexOf(field);
        var end = line.indexOf("\"", start + field.length() + 1);
        return line.substring(start + field.length() +1 , end);
    }

}
