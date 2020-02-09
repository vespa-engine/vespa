// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigRequesterTest;
import com.yahoo.config.subscription.impl.MockConnection;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.CompressionType;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 *
 * Test cases for the "generic" (class-less) subscription mechanism.
 *
 * @author Ulf Lilleengen
 */
public class GenericConfigSubscriberTest {

    @Test
    public void testSubscribeGeneric() {
        ConfigSourceSet sourceSet = new ConfigSourceSet("blabla");
        GenericConfigSubscriber subscriber = createSubscriber();
        final List<String> defContent = List.of("myVal int");
        GenericConfigHandle handle = subscriber.subscribe(new ConfigKey<>("simpletypes", "id", "config"), defContent, sourceSet, JRTConfigRequesterTest.getTestTimingValues());
        assertTrue(subscriber.nextConfig());
        assertTrue(handle.isChanged());
        assertThat(handle.getRawConfig().getPayload().withCompression(CompressionType.UNCOMPRESSED).toString(), is("{}")); // MockConnection returns empty string
        assertFalse(subscriber.nextConfig());
        assertFalse(handle.isChanged());
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
        return new GenericConfigSubscriber(new JRTConfigRequester(new MockConnection(), JRTConfigRequesterTest.getTestTimingValues()));
    }
}
