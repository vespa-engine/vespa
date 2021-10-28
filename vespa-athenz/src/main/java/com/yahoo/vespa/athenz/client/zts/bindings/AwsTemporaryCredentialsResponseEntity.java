// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;

import java.time.Instant;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AwsTemporaryCredentialsResponseEntity {
    private AwsTemporaryCredentials credentials;

    public AwsTemporaryCredentialsResponseEntity(
            @JsonProperty("accessKeyId") String accessKeyId,
            @JsonProperty("secretAccessKey") String secretAccessKey,
            @JsonProperty("sessionToken") String sessionToken,
            @JsonProperty("expiration") Instant expiration) {
        this.credentials = new AwsTemporaryCredentials(accessKeyId, secretAccessKey, sessionToken, expiration);
    }

    public AwsTemporaryCredentials credentials() {
        return credentials;
    }
}
