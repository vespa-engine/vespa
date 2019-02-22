// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.aws;

import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AwsCredentialProviderTest {

    @Test
    public void refreshes_correctly() {
        Clock clock = Clock.systemUTC();
        // Does not require refresh when expires in 10 minutes
        assertFalse(AwsCredentialsProvider.shouldRefresh(getCredentials(clock.instant().plus(Duration.ofMinutes(10)))));

        // Requires refresh when expires in 3 minutes
        assertTrue(AwsCredentialsProvider.shouldRefresh(getCredentials(clock.instant().plus(Duration.ofMinutes(3)))));

        // Requires refresh when expired
        assertTrue(AwsCredentialsProvider.shouldRefresh(getCredentials(clock.instant().minus(Duration.ofMinutes(1)))));

        // Refreshes when no credentials provided
        assertTrue(AwsCredentialsProvider.shouldRefresh(null));
    }

    private AwsTemporaryCredentials getCredentials(Instant expiration) {
        return new AwsTemporaryCredentials("accesskey", "secretaccesskey", "sessionToken", expiration);
    }
}
