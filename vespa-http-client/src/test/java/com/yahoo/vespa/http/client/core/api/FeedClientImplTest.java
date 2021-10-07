// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import org.junit.Test;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author dybis
 */
public class FeedClientImplTest {

    int sleepValueMillis = 1;

    @Test
    public void testCloseWaitTimeOldTimestamp() {
        assertThat(FeedClientImpl.waitForOperations(Instant.now().minusSeconds(1000), sleepValueMillis, 10), is(false));
    }

    @Test
    public void testCloseWaitTimeOutInFutureStillOperations() {
        assertThat(FeedClientImpl.waitForOperations(Instant.now(), sleepValueMillis, 2000), is(true));
    }

}
