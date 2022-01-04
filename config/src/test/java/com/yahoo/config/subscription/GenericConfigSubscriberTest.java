// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigRequesterTest;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.CompressionType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * Test cases for the "generic" (class-less) subscription mechanism.
 *
 * @author Ulf Lilleengen
 */
public class GenericConfigSubscriberTest {
    private static final TimingValues tv = JRTConfigRequesterTest.getTestTimingValues();

    @Test
    public void testSubscribeGeneric() throws InterruptedException {
        JRTConfigRequester requester = new JRTConfigRequester(new MockConnection(), tv);
        GenericConfigSubscriber sub = new GenericConfigSubscriber(requester);
        List<String> defContent = List.of("myVal int");
        GenericConfigHandle handle = sub.subscribe(new ConfigKey<>("simpletypes", "id", "config"),
                                                   defContent,
                                                   tv);
        assertTrue(sub.nextConfig(false));
        assertTrue(handle.isChanged());
        // MockConnection returns empty string
        assertEquals("{}", getConfig(handle));
        assertEquals(1L, handle.getRawConfig().getGeneration());
        assertFalse(sub.nextConfig(false));
        assertFalse(handle.isChanged());

        // Wait some time, config should be the same, but generation should be higher
        Thread.sleep(tv.getFixedDelay() * 3);
        assertEquals("{}", getConfig(handle));
        assertTrue("Unexpected generation (not > 1): " + handle.getRawConfig().getGeneration(), handle.getRawConfig().getGeneration() > 1);
        assertFalse(sub.nextConfig(false));
        assertFalse(handle.isChanged());
    }

    private String getConfig(GenericConfigHandle handle) {
        return handle.getRawConfig().getPayload().withCompression(CompressionType.UNCOMPRESSED).toString();
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid1() {
        createSubscriber().subscribe(null, null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid2() {
        createSubscriber().subscribe(null, null, 0L);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid3() {
        createSubscriber().subscribe(null, null, "");
    }

    private GenericConfigSubscriber createSubscriber() {
        return new GenericConfigSubscriber(new JRTConfigRequester(new JRTConnectionPool(new ConfigSourceSet("foo"), new Supervisor(new Transport())), tv));
    }

}
