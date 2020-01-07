// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.Arrays;

/**
 * Parses predicate queries from JSON.
 *
 * Input JSON is assumed to have the following format:
 * {
 *      "features": [
 *          {"k": "key-name", "v":"value", "s":"0xDEADBEEFDEADBEEF"}
 *      ],
 *      "rangeFeatures": [
 *          {"k": "key-name", "v":42, "s":"0xDEADBEEFDEADBEEF"}
 *      ]
 *  }
 *
 * @author bjorncs
 */
public class PredicateQueryParser {

    private final JsonFactory factory = new JsonFactory();

    @FunctionalInterface
    public interface FeatureHandler<V> {
        void accept(String key, V value, long subqueryBitmap);
    }

    /**
     * Parses predicate query from JSON.
     * @param json JSON input.
     * @param featureHandler The handler is invoked when a feature is parsed.
     * @param rangeFeatureHandler The handler is invoked when a range feature is parsed.
     * @throws IllegalArgumentException If JSON is invalid.
     */
    public void parseJsonQuery(
            String json, FeatureHandler<String> featureHandler, FeatureHandler<Long> rangeFeatureHandler)
    throws IllegalArgumentException {

        try (JsonParser parser = factory.createParser(json)) {
            skipToken(parser, JsonToken.START_OBJECT);
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                switch (fieldName) {
                    case "features":
                        parseFeatures(parser, JsonParser::getText, featureHandler);
                        break;
                    case "rangeFeatures":
                        parseFeatures(parser, JsonParser::getLongValue, rangeFeatureHandler);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid field name: " + fieldName);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new AssertionError("This should never happen when parsing from a String", e);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Parsing query from JSON failed: '%s'", json), e);
        }
    }

    private static <V> void parseFeatures(
            JsonParser parser, ValueParser<V> valueParser, FeatureHandler<V> featureHandler) throws IOException {
        skipToken(parser, JsonToken.START_ARRAY);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            parseFeature(parser, valueParser, featureHandler);
        }
    }

    private static <V> void parseFeature(
            JsonParser parser, ValueParser<V> valueParser, FeatureHandler<V> featureHandler) throws IOException {
        String key = null;
        V value = null;
        long subqueryBitmap = SubqueryBitmap.DEFAULT_VALUE; // Specifying subquery bitmap is optional.

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            skipToken(parser, JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT);
            switch (fieldName) {
                case "k":
                    key = parser.getText();
                    break;
                case "v":
                    value = valueParser.parse(parser);
                    break;
                case "s":
                    subqueryBitmap = fromHexString(parser.getText());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid field name: " + fieldName);
            }
        }
        if (key == null) {
            throw new IllegalArgumentException(
                    String.format("Feature key is missing! (%s)", parser.getCurrentLocation()));
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("Feature value is missing! (%s)", parser.getCurrentLocation()));
        }
        featureHandler.accept(key, value, subqueryBitmap);
    }

    private static void skipToken(JsonParser parser, JsonToken... expected) throws IOException {
        JsonToken actual = parser.nextToken();
        if (Arrays.stream(expected).noneMatch(e -> e.equals(actual))) {
            throw new IllegalArgumentException(
                    String.format("Expected a token in %s, got %s (%s).",
                            Arrays.toString(expected), actual, parser.getTokenLocation()));
        }
    }

    private static long fromHexString(String subqueryBitmap) {
        if (!subqueryBitmap.startsWith("0x")) {
            throw new IllegalArgumentException("Not a valid subquery bitmap ('0x' prefix missing): " + subqueryBitmap);
        }
        return Long.parseUnsignedLong(subqueryBitmap.substring(2), 16);
    }

    @FunctionalInterface
    private interface ValueParser<V> {
        V parse(JsonParser parser) throws IOException;
    }

}
