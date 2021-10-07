// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import org.junit.Test;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 * @since 5.1.11
 */
public class DelayedResponseTest {

    private static final String configId = "id";
    private static final String namespace = "bar";

    @Test
    public void basic() {
        ConfigTester tester = new ConfigTester();
        final long returnTime = System.currentTimeMillis();
        final long timeout = 1;
        final String configName = "foo";
        final JRTServerConfigRequest request = tester.createRequest(configName, configId, namespace, timeout);
        DelayedResponse delayedResponse = new DelayedResponse(request, returnTime);
        assertThat(delayedResponse.getRequest(), is(request));
        assertThat(delayedResponse.getReturnTime(), is(returnTime));
        assertTrue(delayedResponse.getDelay(TimeUnit.SECONDS) < returnTime);

        DelayedResponse before = new DelayedResponse(request, returnTime - 1000L);
        DelayedResponse after = new DelayedResponse(request, returnTime + 1000L);

        assertThat(delayedResponse.compareTo(delayedResponse), is(0));
        assertThat(delayedResponse.compareTo(before), is(1));
        assertThat(delayedResponse.compareTo(after), is(-1));
        assertThat(delayedResponse.compareTo(new Delayed() {
            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(Delayed o) {
                return 0;
            }
        }), is(0));
    }

    @Test
    public void testDelayedResponse() {
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
        assertTrue("delayed1=" + delayed1.getReturnTime() + ", delayed2=" +
                delayed2.getReturnTime() + ": delayed2 should be greater than delayed1",
                delayed2.getReturnTime() > delayed1.getReturnTime());

        // Test compareTo() method
        assertThat(delayed1.compareTo(delayed1), is(0));
        assertThat(delayed1.compareTo(delayed2), is(-1));
        assertThat(delayed2.compareTo(delayed1), is(1));
    }

}
