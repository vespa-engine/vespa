// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.yahoo.processing.IllegalInputException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser that consumes json or cbor and creates a single level key value map by dotting nested objects.
 * This is specially tailored for the query input coming as post body.
 * It does the cheapest possible parsing delaying number parsing to where it is needed and avoids dreaded toString()
 * of complicated json object trees.
 *
 * @author baldersheim
 */
class Json2SingleLevelMap {

    private static final ObjectMapper jsonMapper = createJsonMapper();
    private static final ObjectMapper cborMapper = createCborMapper();

    private final byte [] buf;

    private final JsonParser parser;
    Json2SingleLevelMap(InputStream data) {
        this(data, jsonMapper, true);
    }

    static Json2SingleLevelMap ofCbor(InputStream data) {
        return new Json2SingleLevelMap(data, cborMapper, false);
    }

    private Json2SingleLevelMap(InputStream data, ObjectMapper mapper, boolean retainRawBytes) {
        try {
            byte[] raw = data.readAllBytes();
            buf = retainRawBytes ? raw : null;
            parser = mapper.createParser(raw);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }
    }

    private static ObjectMapper createJsonMapper() {
        return Jackson.createMapper(new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                // allow newline/tab inside JSON strings, mainly for large YQL/grouping statements:
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, true)
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true));
    }

    private static ObjectMapper createCborMapper() {
        CBORFactory factory = new CBORFactory();
        factory.setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
        return new ObjectMapper(factory);
    }

    Map<String, String> parse() {
        try {
            Map<String, String> map = new HashMap<>();
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalInputException("Expected start of object, got '" + parser.currentToken() + "'");
            }
            parse(map, "");
            return map;
        } catch (JsonParseException e) {
            throw new IllegalInputException("Json parse error.", e);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }
    }

    void parse(Map<String, String> map, String parent) throws IOException {
        for (parser.nextToken(); parser.currentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parent + parser.currentName();
            JsonToken token = parser.nextToken();
            if ((token == JsonToken.VALUE_STRING) ||
                (token == JsonToken.VALUE_NUMBER_FLOAT) ||
                (token == JsonToken.VALUE_NUMBER_INT) ||
                (token == JsonToken.VALUE_TRUE) ||
                (token == JsonToken.VALUE_FALSE) ||
                (token == JsonToken.VALUE_NULL)) {
                map.put(fieldName, parser.getText());
            } else if (token == JsonToken.START_ARRAY) {
                map.put(fieldName, skipChildren(parser, buf));
            } else if (token == JsonToken.START_OBJECT) {
                if (fieldName.startsWith("input.") || fieldName.equals("select.where") || fieldName.equals("select.grouping")) {
                    map.put(fieldName, skipChildren(parser, buf));
                } else {
                    parse(map, fieldName + ".");
                }
            } else {
                throw new IllegalInputException("In field '" + fieldName + "', got unknown json token '" + token.asString() + "'");
            }
        }
    }

    /** Skips the current structured value (array or object) and returns its content as a JSON string. */
    private String skipChildren(JsonParser parser, byte [] input) throws IOException {
        if (input != null) {
            // JSON fast path: extract raw bytes directly from the input buffer
            JsonLocation start = parser.currentLocation();
            parser.skipChildren();
            JsonLocation end = parser.currentLocation();
            int offset = (int)start.getByteOffset() - 1;
            return new String(input, offset, (int)(end.getByteOffset() - offset), StandardCharsets.UTF_8);
        } else {
            // CBOR (or other binary format): transcode the subtree to a JSON string.
            // copyCurrentStructure leaves the parser at the END token, matching skipChildren() behavior.
            StringWriter writer = new StringWriter();
            try (var gen = jsonMapper.getFactory().createGenerator(writer)) {
                gen.copyCurrentStructure(parser);
            }
            return writer.toString();
        }
    }

}
