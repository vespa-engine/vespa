// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.aws;

import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import org.junit.Assert;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertFalse;

/**
 * @author tokle
 */
public class AwsCredentialsTest {

    @Test
    public void refreshes_correctly() {
        Clock clock = Clock.systemUTC();
        // Does not require refresh when expires in 10 minutes
        assertFalse(AwsCredentials.shouldRefresh(getCredentials(clock.instant().plus(Duration.ofMinutes(10)))));

        // Requires refresh when expires in 3 minutes
        Assert.assertTrue(AwsCredentials.shouldRefresh(getCredentials(clock.instant().plus(Duration.ofMinutes(3)))));

        // Requires refresh when expired
        Assert.assertTrue(AwsCredentials.shouldRefresh(getCredentials(clock.instant().minus(Duration.ofMinutes(1)))));

        // Refreshes when no credentials provided
        Assert.assertTrue(AwsCredentials.shouldRefresh(null));
    }

    private AwsTemporaryCredentials getCredentials(Instant expiration) {
        return new AwsTemporaryCredentials("accesskey", "secretaccesskey", "sessionToken", expiration);
    }
}
