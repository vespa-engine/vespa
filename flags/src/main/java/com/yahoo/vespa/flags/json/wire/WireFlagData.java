// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WireFlagData {
    @JsonProperty("id") public String id;
    @JsonProperty("rules") public List<WireRule> rules;
    @JsonProperty("attributes") public Map<String, String> defaultFetchVector;

    private static final ObjectMapper mapper = new ObjectMapper();

    public byte[] serializeToBytes() {
        return uncheck(() -> mapper.writeValueAsBytes(this));
    }

    public String serializeToJson() {
        return uncheck(() -> mapper.writeValueAsString(this));
    }

    public JsonNode serializeToJsonNode() {
        return uncheck(() -> mapper.valueToTree(this));
    }

    public void serializeToOutputStream(OutputStream outputStream) {
        uncheck(() -> mapper.writeValue(outputStream, this));
    }

    public static WireFlagData deserialize(byte[] bytes) {
        return uncheck(() -> mapper.readValue(bytes, WireFlagData.class));
    }

    public static WireFlagData deserialize(String string) {
        return uncheck(() -> mapper.readValue(string, WireFlagData.class));
    }

    public static WireFlagData deserialize(InputStream inputStream) {
        return uncheck(() -> mapper.readValue(inputStream, WireFlagData.class));
    }
}
