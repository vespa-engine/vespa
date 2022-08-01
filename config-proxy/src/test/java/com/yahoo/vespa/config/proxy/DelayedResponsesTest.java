// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class DelayedResponsesTest {

    @Test
    void basic() throws InterruptedException {
        ConfigTester tester = new ConfigTester();
        DelayedResponses responses = new DelayedResponses();
        DelayedResponse delayedResponse = new DelayedResponse(tester.createRequest("foo", "id", "bar", 10));
        responses.add(delayedResponse);

        assertEquals(1, responses.size());
        assertEquals(delayedResponse, responses.responses().take());
        assertEquals(0, responses.size());

        responses.add(delayedResponse);
        assertEquals(1, responses.size());
        responses.remove(delayedResponse);
        assertEquals(0, responses.size());
    }

}
