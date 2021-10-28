// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.time.Instant;

/**
 * @author mortent
 */
public class AwsTemporaryCredentials {
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;
    private final Instant expiration;

    public AwsTemporaryCredentials(String accessKeyId, String secretAccessKey, String sessionToken, Instant expiration) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
        this.expiration = expiration;
    }

    public String accessKeyId() {
        return accessKeyId;
    }

    public String secretAccessKey() {
        return secretAccessKey;
    }

    public String sessionToken() {
        return sessionToken;
    }

    public Instant expiration() {
        return expiration;
    }
}
