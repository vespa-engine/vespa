// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WireFlagDataList {
    @JsonProperty("flags")
    public List<WireFlagData> flags = new ArrayList<>();

    private static final ObjectMapper mapper = new ObjectMapper();

    public void serializeToOutputStream(OutputStream outputStream) {
        uncheck(() -> mapper.writeValue(outputStream, this));
    }

    public byte[] serializeToBytes() {
        return uncheck(() -> mapper.writeValueAsBytes(this));
    }

    public static WireFlagDataList deserializeFrom(byte[] bytes) {
        return uncheck(() -> mapper.readValue(bytes, WireFlagDataList.class));
    }
}
