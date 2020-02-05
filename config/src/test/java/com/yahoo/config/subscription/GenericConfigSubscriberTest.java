// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.util.*;

import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigRequesterTest;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.protocol.CompressionType;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 *
 * Test cases for the "generic" (class-less) subscription mechanism.
 *
 * @author Ulf Lilleengen
 */
public class GenericConfigSubscriberTest {

    @Test
    public void testSubscribeGeneric() {
        Map<ConfigSourceSet, JRTConfigRequester> requesters = new HashMap<>();
        ConfigSourceSet sourceSet = new ConfigSourceSet("blabla");
        requesters.put(sourceSet, JRTConfigRequester.get(new MockConnection(), JRTConfigRequesterTest.getTestTimingValues()));
        GenericConfigSubscriber sub = new GenericConfigSubscriber(requesters);
        final List<String> defContent = Arrays.asList("myVal int");
        GenericConfigHandle handle = sub.subscribe(new ConfigKey<>("simpletypes", "id", "config"), defContent, sourceSet, JRTConfigRequesterTest.getTestTimingValues());
        assertTrue(sub.nextConfig());
        assertTrue(handle.isChanged());
        assertThat(handle.getRawConfig().getPayload().withCompression(CompressionType.UNCOMPRESSED).toString(), is("{}")); // MockConnection returns empty string
        assertFalse(sub.nextConfig());
        assertFalse(handle.isChanged());
    }

    @Test
    public void testGenericRequesterPooling() {
        ConfigSourceSet source1 = new ConfigSourceSet("tcp/foo:78");
        ConfigSourceSet source2 = new ConfigSourceSet("tcp/bar:79");
        JRTConfigRequester req1 = JRTConfigRequester.get(new JRTConnectionPool(source1), JRTConfigRequesterTest.getTestTimingValues());
        JRTConfigRequester req2 = JRTConfigRequester.get(new JRTConnectionPool(source2), JRTConfigRequesterTest.getTestTimingValues());
        Map<ConfigSourceSet, JRTConfigRequester> requesters = new LinkedHashMap<>();
        requesters.put(source1, req1);
        requesters.put(source2, req2);
        GenericConfigSubscriber sub = new GenericConfigSubscriber(requesters);
        assertEquals(sub.requesters().get(source1).getConnectionPool().getCurrent().getAddress(), "tcp/foo:78");
        assertEquals(sub.requesters().get(source2).getConnectionPool().getCurrent().getAddress(), "tcp/bar:79");
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid1() {
        GenericConfigSubscriber sub = new GenericConfigSubscriber();
        sub.subscribe(null, null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid2() {
        GenericConfigSubscriber sub = new GenericConfigSubscriber();
        sub.subscribe(null, null, 0L);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testOverriddenSubscribeInvalid3() {
        GenericConfigSubscriber sub = new GenericConfigSubscriber();
        sub.subscribe(null, null, "");
    }
}
