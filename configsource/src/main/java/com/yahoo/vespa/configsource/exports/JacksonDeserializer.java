// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static com.yahoo.vespa.configsource.util.ExceptionUtil.uncheck;

/**
 * Deserialize byte[] as a UTF-8 JSON into a Jackson object.
 *
 * @author hakon
 */
public class JacksonDeserializer<T> implements Deserializer<T> {
    private static ObjectMapper mapper = new ObjectMapper();

    private final Class<T> jacksonClass;

    public JacksonDeserializer(Class<T> jacksonClass) {
        this.jacksonClass = jacksonClass;
    }

    @Override
    public T deserialize(byte[] bytes) {
        // Require UTF-8 instead of relying on implicit encoding used by readValue(byte[],...)
        String json = new String(bytes, StandardCharsets.UTF_8);
        return uncheck(() -> mapper.readValue(json, jacksonClass));
    }
}
