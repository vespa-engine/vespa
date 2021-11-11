// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigRequesterTest;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.CompressionType;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<ConfigSourceSet, JRTConfigRequester> requesters = new HashMap<>();
        ConfigSourceSet sourceSet = new ConfigSourceSet("blabla");
        requesters.put(sourceSet, new JRTConfigRequester(new MockConnection(), tv));
        GenericConfigSubscriber sub = new GenericConfigSubscriber(requesters);
        final List<String> defContent = List.of("myVal int");
        GenericConfigHandle handle = sub.subscribe(new ConfigKey<>("simpletypes", "id", "config"),
                                                   defContent,
                                                   sourceSet,
                                                   tv);
        assertTrue(sub.nextConfig(false));
        assertTrue(handle.isChanged());
        // MockConnection returns empty string
        assertEquals("{}", getConfig(handle));
        assertEquals(1L, handle.getRawConfig().getGeneration());
        assertFalse(sub.nextConfig(false));
        assertFalse(handle.isChanged());

        // Wait some time, config should be the same, but generation should be higher
        Thread.sleep(tv.getFixedDelay() * 2);
        assertEquals("{}", getConfig(handle));
        assertTrue(handle.getRawConfig().getGeneration() > 1);
        assertFalse(sub.nextConfig(false));
        assertFalse(handle.isChanged());
    }

    private String getConfig(GenericConfigHandle handle) {
        return handle.getRawConfig().getPayload().withCompression(CompressionType.UNCOMPRESSED).toString();
    }

    @Test
    public void testGenericRequesterPooling() {
        ConfigSourceSet source1 = new ConfigSourceSet("tcp/foo:78");
        ConfigSourceSet source2 = new ConfigSourceSet("tcp/bar:79");
        JRTConfigRequester req1 = JRTConfigRequester.create(source1, tv);
        JRTConfigRequester req2 = JRTConfigRequester.create(source2, tv);
        Map<ConfigSourceSet, JRTConfigRequester> requesters = new LinkedHashMap<>();
        requesters.put(source1, req1);
        requesters.put(source2, req2);
        GenericConfigSubscriber sub = new GenericConfigSubscriber(requesters);
        assertEquals(sub.requesters().get(source1).getConnectionPool().getCurrent().getAddress(), "tcp/foo:78");
        assertEquals(sub.requesters().get(source2).getConnectionPool().getCurrent().getAddress(), "tcp/bar:79");
        for (JRTConfigRequester requester : requesters.values()) {
            requester.close();
        }
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
        return new GenericConfigSubscriber(Map.of(
                new ConfigSourceSet("blabla"),
                new JRTConfigRequester(new MockConnection(), JRTConfigRequesterTest.getTestTimingValues())));
    }

}
