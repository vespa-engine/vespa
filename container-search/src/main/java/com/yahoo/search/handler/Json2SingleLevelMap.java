package com.yahoo.search.handler;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.processing.IllegalInputException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser that consumes json and creates a single level key value map by dotting nested objects.
 * This is specially tailored for the json query input coming as post.
 * It does the cheapest possible json parsing delaying number parsing to where it is needed and avoids dreaded toString()
 * of complicated json object trees.
 *
 * @author baldersheim
 */
class Json2SingleLevelMap {

    private static final ObjectMapper jsonMapper = createMapper();

    private final byte [] buf;

    private final JsonParser parser;
    Json2SingleLevelMap(InputStream data) {
        try {
            buf = data.readAllBytes();
            parser = jsonMapper.createParser(buf);
        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }
    }

    private static ObjectMapper createMapper() {
        var jsonFactory = new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
                .build();
        return new ObjectMapper(jsonFactory);
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
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parent + parser.getCurrentName();
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
                if (fieldName.equals("select.where") || fieldName.equals("select.grouping")) {
                    map.put(fieldName, skipChildren(parser, buf));
                } else {
                    parse(map, fieldName + ".");
                }
            } else {
                throw new IllegalInputException("In field '" + fieldName + "', got unknown json token '" + token.asString() + "'");
            }
        }
    }

    private String skipChildren(JsonParser parser, byte [] input) throws IOException {
        JsonLocation start = parser.getCurrentLocation();
        parser.skipChildren();
        JsonLocation end = parser.getCurrentLocation();
        int offset = (int)start.getByteOffset() - 1;
        return new String(input, offset, (int)(end.getByteOffset() - offset), StandardCharsets.UTF_8);
    }

}
