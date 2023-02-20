// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.serialization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.PredicateQueryParser;
import com.yahoo.search.predicate.SubqueryBitmap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;


/**
 * Converts {@link PredicateQuery} to and from JSON
 *
 * Example:
 *  {
 *      features: [
 *          {"k": "key-name", "v":"value", "s":"0xDEADBEEFDEADBEEF"}
 *      ],
 *      rangeFeatures: [
 *          {"k": "key-name", "v":42, "s":"0xDEADBEEFDEADBEEF"}
 *      ]
 *  }
 *
 * @author bjorncs
 */
public class PredicateQuerySerializer {

    private final JsonFactory factory = new JsonFactory();
    private final PredicateQueryParser parser = new PredicateQueryParser();

    public String toJSON(PredicateQuery query) {
        try {
            StringWriter writer = new StringWriter(1024);
            toJSON(query, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void toJSON(PredicateQuery query, Writer writer) throws IOException {
        try (JsonGenerator g = factory.createGenerator(writer)) {
            g.writeStartObject();

            // Write features
            g.writeArrayFieldStart("features");
            for (PredicateQuery.Feature feature : query.getFeatures()) {
                writeFeature(feature.key, feature.value, feature.subqueryBitmap, g, JsonGenerator::writeStringField);
            }
            g.writeEndArray();

            // Write rangeFeatures
            g.writeArrayFieldStart("rangeFeatures");
            for (PredicateQuery.RangeFeature rangeFeature : query.getRangeFeatures()) {
                writeFeature(rangeFeature.key, rangeFeature.value, rangeFeature.subqueryBitmap, g,
                        JsonGenerator::writeNumberField);
            }
            g.writeEndArray();

            g.writeEndObject();
        }
    }

    private static <T> void writeFeature(
            String key, T value, long subqueryBitmap, JsonGenerator g, ValueWriter<T> valueWriter)
            throws IOException {

        g.writeStartObject();
        g.writeStringField("k", key);
        valueWriter.write(g, "v", value);
        if (subqueryBitmap != SubqueryBitmap.DEFAULT_VALUE) {
            g.writeStringField("s", toHexString(subqueryBitmap));
        }
        g.writeEndObject();
    }

    @FunctionalInterface
    private interface ValueWriter<T> {
        void write(JsonGenerator g, String key, T value) throws IOException;
    }

    public PredicateQuery fromJSON(String json) {
        PredicateQuery query = new PredicateQuery();
        parser.parseJsonQuery(json, query::addFeature, query::addRangeFeature);
        return query;
    }

    public static List<PredicateQuery> parseQueriesFromFile(String queryFile, int maxQueryCount) throws IOException {
        PredicateQuerySerializer serializer = new PredicateQuerySerializer();
        try (BufferedReader reader = new BufferedReader(new FileReader(queryFile), 8 * 1024)) {
            return reader.lines()
                    .limit(maxQueryCount)
                    .map(serializer::fromJSON)
                    .toList();
        }
    }

    private static String toHexString(long subqueryBitMap) {
        return "0x" + Long.toHexString(subqueryBitMap);
    }

}
