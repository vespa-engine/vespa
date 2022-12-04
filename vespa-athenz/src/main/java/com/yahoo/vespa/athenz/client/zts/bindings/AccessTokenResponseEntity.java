// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessTokenResponseEntity {
    private final AthenzAccessToken accessToken;
    private final Instant expiryTime;
    private final List<AthenzRole> roles;

    public AccessTokenResponseEntity(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn,
            @JsonProperty("scope") String roles) {

        this.accessToken =  new AthenzAccessToken(accessToken);
        // We do not know from when, so best we can do is assume now ...
        this.expiryTime = Instant.now().plusSeconds(expiresIn);
        this.roles = Stream.of(roles.split(" "))
                .map(AthenzResourceName::fromString)
                .map(AthenzRole::fromResourceName)
                .collect(Collectors.toList());
    }

    public AthenzAccessToken accessToken() {
        return accessToken;
    }

    public Instant expiryTime() {
        return expiryTime;
    }

    public List<AthenzRole> roles() {
        return roles;
    }
}
