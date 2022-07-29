// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 * @since 5.1.11
 */
public class DelayedResponseTest {

    private static final String configId = "id";
    private static final String namespace = "bar";

    @Test
    void basic() {
        ConfigTester tester = new ConfigTester();
        final long returnTime = System.currentTimeMillis();
        final long timeout = 1;
        final String configName = "foo";
        final JRTServerConfigRequest request = tester.createRequest(configName, configId, namespace, timeout);
        DelayedResponse delayedResponse = new DelayedResponse(request, returnTime);
        assertEquals(request, delayedResponse.getRequest());
        assertEquals(returnTime, delayedResponse.getReturnTime().longValue());
        assertTrue(delayedResponse.getDelay(TimeUnit.SECONDS) < returnTime);

        DelayedResponse before = new DelayedResponse(request, returnTime - 1000L);
        DelayedResponse after = new DelayedResponse(request, returnTime + 1000L);

        assertEquals(0, delayedResponse.compareTo(delayedResponse));
        assertEquals(1, delayedResponse.compareTo(before));
        assertEquals(-1, delayedResponse.compareTo(after));
        assertEquals(0, delayedResponse.compareTo(new Delayed() {
            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(Delayed o) {
                return 0;
            }
        }));
    }

    @Test
    void testDelayedResponse() {
        ConfigTester tester = new ConfigTester();
        final long timeout = 20000;
        JRTServerConfigRequest request1 = tester.createRequest("baz", configId, namespace, timeout);
        DelayedResponse delayed1 = new DelayedResponse(request1);
        assertTrue(delayed1.getReturnTime() > System.currentTimeMillis());
        assertTrue(delayed1.getDelay(TimeUnit.MILLISECONDS) > 0);
        assertTrue(delayed1.getDelay(TimeUnit.MILLISECONDS) <= timeout);

        // Just to make sure we do not get requests within the same millisecond
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // New request, should have larger delay than the first
        JRTServerConfigRequest request2 = tester.createRequest("baz", configId, namespace, timeout);
        DelayedResponse delayed2 = new DelayedResponse(request2);
        assertTrue(delayed2.getReturnTime() > delayed1.getReturnTime(),
                                 "delayed1=" + delayed1.getReturnTime() + ", delayed2=" +
                                                          delayed2.getReturnTime() + ": delayed2 should be greater than delayed1");

        // Test compareTo() method
        assertEquals(0, delayed1.compareTo(delayed1));
        assertEquals(-1, delayed1.compareTo(delayed2));
        assertEquals(1, delayed2.compareTo(delayed1));
    }

}
