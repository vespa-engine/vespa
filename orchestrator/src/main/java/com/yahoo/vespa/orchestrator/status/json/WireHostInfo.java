// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Handles serialization/deserialization of HostInfo to/from byte array.
 *
 * @author hakonhall
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WireHostInfo {
    private static final ObjectMapper mapper = new ObjectMapper();

    @JsonProperty("status") public String status;
    @JsonProperty("suspendedSince") public Long suspendedSinceInMillis;

    public static HostInfo deserialize(byte[] bytes) {
        String serializedString = new String(bytes, StandardCharsets.UTF_8);
        WireHostInfo wireHostInfo = uncheck(() -> mapper.readValue(serializedString, WireHostInfo.class));
        return HostInfo.createSuspended(HostStatus.valueOf(Objects.requireNonNull(wireHostInfo.status)),
                                      Instant.ofEpochMilli(Objects.requireNonNull(wireHostInfo.suspendedSinceInMillis)));
    }

    public static byte[] serialize(HostInfo hostInfo) {
        if (!hostInfo.status().isSuspended()) {
            throw new IllegalArgumentException("Serialization of unsuspended status is not supported: " + hostInfo.status());
        }

        WireHostInfo wireHostInfo = new WireHostInfo();
        wireHostInfo.status = hostInfo.status().name();
        wireHostInfo.suspendedSinceInMillis = hostInfo.suspendedSince().get().toEpochMilli();

        return uncheck(() -> mapper.writeValueAsBytes(wireHostInfo));
    }
}
