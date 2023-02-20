// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import com.yahoo.search.predicate.PredicateQuery;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;


/**
 * Parses query file containing Vespa queries using the deprecated predicate format (query properties - not YQL).
 *
 * @author bjorncs
 */
public class VespaQueryParser {

    /**
     * Parses a query formatted using the deprecated boolean query format (query properties).
     */
    public static List<PredicateQuery> parseQueries(String queryFile, int maxQueryCount) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(queryFile), 8 * 1024)) {
            List<PredicateQuery> queries = reader.lines()
                    .limit(maxQueryCount)
                    .map(VespaQueryParser::parseQueryFromQueryProperties)
                    .toList();
            return queries;
        }
    }

    public static PredicateQuery parseQueryFromQueryProperties(String queryString) {
        try {
            // Decode the URL in case the query property content is escaped.
            queryString = URLDecoder.decode(queryString, "UTF-8");
            PredicateQuery query = new PredicateQuery();
            extractQueryValues(queryString, "boolean.attributes", query::addFeature);
            extractQueryValues(queryString, "boolean.rangeAttributes",
                    (k, v) -> query.addRangeFeature(k, Integer.parseInt(v)));
            return query;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void extractQueryValues(String query, String prefix, BiConsumer<String, String> registerTerm) {
        int rangeIndex = query.indexOf(prefix);
        if (rangeIndex != -1) {
            // Adding 2 to skip '={'
            int startIndex = rangeIndex + prefix.length() + 2;
            // '%7D' represents the end of the predicate string.
            int endIndex = query.indexOf("}", startIndex);
            String rangeString = query.substring(startIndex, endIndex);
            List<Feature> features = new ArrayList<>();
            String[] keyValuePairs = rangeString.split(",");

            for (String keyValuePair : keyValuePairs) {
                String[] keyAndValue = keyValuePair.split(":");
                // If not colon is found, the string is part of the previous value.
                if (keyAndValue.length == 1) {
                    Feature feature = features.get(features.size() - 1);
                    feature.value += ("," + keyValuePair);
                } else {
                    features.add(new Feature(keyAndValue[0], keyAndValue[1]));
                }
            }
            features.stream().forEach(f -> registerTerm.accept(f.key, f.value));
        }
    }

    private static class Feature {
        public final String key;
        public String value;

        private Feature(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Feature feature = (Feature) o;

            if (!key.equals(feature.key)) return false;
            if (!value.equals(feature.value)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }
    }

}
