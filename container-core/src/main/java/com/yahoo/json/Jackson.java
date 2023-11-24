// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class to get jackson json parsers with common settings.
 * TODO Belong in vespajlib, but due to OSGI resides here.
 * There is also a copy in testutil for testing in non OSGI modules
 */
public class Jackson {
    private static final ObjectMapper mapperInstance = createMapper();
    /// Create an ObjectMapper based on given factory, adds additional recommended settings
    public static ObjectMapper createMapper() {
        return createMapper(new JsonFactoryBuilder());
    }

    /// Create an ObjectMapper based on given factory, adds additional recommended settings
    public static ObjectMapper createMapper(JsonFactoryBuilder jsonFactoryBuilder) {
        JsonFactory jsonFactory = jsonFactoryBuilder
                // This changes in 2.16, needs to consider what to do
                .configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, true)
                .build();
        return new ObjectMapper(jsonFactory);
    }

    /// Return a default ObjectMapper with recommended settings
    public static ObjectMapper mapper() { return mapperInstance; }
}
