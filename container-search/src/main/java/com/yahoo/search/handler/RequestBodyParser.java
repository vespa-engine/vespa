// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.slime.ArrayInserter;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectInserter;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses a JSON or CBOR request body into maps for query processing.
 * <p>
 * Scalar properties are dot-flattened into a {@code Map<String, String>}.
 * Structured fields ({@code input.*}, {@code select.where}, {@code select.grouping})
 * are kept as Slime {@link Inspector} objects to avoid unnecessary serialization round-trips.
 *
 * @author andreer
 */
class RequestBodyParser {

    private static final JsonFactory jsonFactory = new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
            .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
            .build();
    private static final CBORFactory cborFactory = new CBORFactory();

    private final Map<String, String> stringMap;
    private final Map<String, Inspector> inspectorMap;

    Map<String, String> stringMap() { return stringMap; }
    Map<String, Inspector> inspectorMap() { return inspectorMap; }

    static RequestBodyParser empty() { return new RequestBodyParser(); }

    static RequestBodyParser parseJson(InputStream data) {
        return new RequestBodyParser(data, jsonFactory);
    }

    static RequestBodyParser parseCbor(InputStream data) {
        return new RequestBodyParser(data, cborFactory);
    }

    private RequestBodyParser() {
        this.stringMap = new HashMap<>();
        this.inspectorMap = Map.of();
    }

    private RequestBodyParser(InputStream data, JsonFactory factory) {
        this.stringMap = new HashMap<>();
        this.inspectorMap = new HashMap<>();
        try (JsonParser parser = factory.createParser(data)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalInputException("Expected start of object, got " + parser.currentToken());
            }
            flatten(parser, "");
        } catch (IllegalInputException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            throw new IllegalInputException("Error parsing request body: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }
    }

    private void flatten(JsonParser parser, String prefix) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = prefix + parser.currentName();
            JsonToken token = parser.nextToken();
            if (token.isScalarValue()) {
                stringMap.put(fieldName, parser.getText());
            } else if (token == JsonToken.START_ARRAY) {
                if (isStructuredField(fieldName)) {
                    inspectorMap.put(fieldName, parseToSlime(parser));
                } else {
                    stringMap.put(fieldName, parseToJsonString(parser));
                }
            } else if (token == JsonToken.START_OBJECT) {
                if (isStructuredField(fieldName)) {
                    inspectorMap.put(fieldName, parseToSlime(parser));
                } else {
                    flatten(parser, fieldName + ".");
                }
            }
        }
    }

    /** Parse current array or object into a Slime Inspector for structured fields */
    private static Inspector parseToSlime(JsonParser parser) throws IOException {
        Slime slime = new Slime();
        decodeValue(parser, new com.yahoo.slime.SlimeInserter(slime));
        return slime.get();
    }

    private static final ArrayInserter arrayInserter = new ArrayInserter(null);
    private static final ObjectInserter objectInserter = new ObjectInserter(null, "");

    private static void decodeValue(JsonParser parser, com.yahoo.slime.Inserter inserter) throws IOException {
        switch (parser.currentToken()) {
            case START_OBJECT -> {
                Cursor cursor = inserter.insertOBJECT();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.FIELD_NAME)
                        throw new IllegalInputException("Expected field name in object, got " + parser.currentToken()
                                                        + ". CBOR maps must use text string keys.");
                    String key = parser.currentName();
                    parser.nextToken();
                    decodeValue(parser, objectInserter.adjust(cursor, key));
                }
            }
            case START_ARRAY -> {
                Cursor cursor = inserter.insertARRAY();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    decodeValue(parser, arrayInserter.adjust(cursor));
                }
            }
            case VALUE_STRING -> inserter.insertSTRING(parser.getText());
            case VALUE_NUMBER_INT -> {
                if (parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER)
                    throw new IllegalInputException("Integer value too large: " + parser.getText());
                inserter.insertLONG(parser.getLongValue());
            }
            case VALUE_NUMBER_FLOAT -> inserter.insertDOUBLE(parser.getDoubleValue());
            case VALUE_TRUE -> inserter.insertBOOL(true);
            case VALUE_FALSE -> inserter.insertBOOL(false);
            case VALUE_NULL -> inserter.insertNIX();
            case VALUE_EMBEDDED_OBJECT ->
                throw new IllegalInputException("Unsupported value type (binary/byte string) in query body");
            default -> throw new IllegalInputException("Unexpected value type in query body: " + parser.currentToken());
        }
    }

    /** Serialize current value to a JSON string (for non-structured array/object fields) */
    private static String parseToJsonString(JsonParser parser) throws IOException {
        // Build Slime sub-tree and serialize to JSON -- works for both JSON and CBOR input
        return SlimeUtils.toJson(parseToSlime(parser));
    }

    private static boolean isStructuredField(String fieldName) {
        return fieldName.startsWith("input.")
                || fieldName.equals("select.where")
                || fieldName.equals("select.grouping");
    }
}
