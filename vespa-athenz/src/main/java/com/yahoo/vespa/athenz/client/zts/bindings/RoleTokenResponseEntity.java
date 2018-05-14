// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yahoo.vespa.athenz.api.ZToken;

import java.io.IOException;
import java.time.Instant;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleTokenResponseEntity {
    public final ZToken token;
    public final Instant expiryTime;

    @JsonCreator
    public RoleTokenResponseEntity(@JsonProperty("token") @JsonDeserialize(using = RoleTokenDeserializer.class) ZToken token,
                                   @JsonProperty("expiryTime") Instant expiryTime) {
        this.token = token;
        this.expiryTime = expiryTime;
    }

    public static class RoleTokenDeserializer extends JsonDeserializer<ZToken> {
        @Override
        public ZToken deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            return new ZToken(jsonParser.getValueAsString());
        }
    }

}
