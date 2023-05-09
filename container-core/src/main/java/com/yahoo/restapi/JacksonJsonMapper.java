// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Default Jackson {@link ObjectMapper} instance shared by {@link com.yahoo.restapi}.
 *
 * @author bjorncs
 */
class JacksonJsonMapper {

    static final ObjectMapper instance = new ObjectMapper(createFactory())
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private JacksonJsonMapper() {}

    private static JsonFactory createFactory() {
        return new JsonFactoryBuilder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();

    }
}
