// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests reading of a config containing
 * <ul>
 * <li>Missing values
 * <li>Default values
 * </ul>
 * <p/>
 * for
 * <p/>
 * <ul>
 * <li>String and
 * <li>Reference
 * </ul>
 *
 * @author hmusum
 */
public class DefaultConfigTest {

    static final String CONFIG_ID = "raw:" +
            "nondefaultstring ####-------missing--------\n" +
            "defaultstring \"thedefault\"\n" +
            "nondefaultreference ####-------missing--------\n" +
            "defaultreference \"thedefault\"\n";

    @Test(expected = IllegalArgumentException.class)
    public void testFailUponUnitializedValue() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        subscriber.subscribe(DefaulttestConfig.class, "raw:defaultstring \"new value\"");
        subscriber.nextConfig(false);
        subscriber.close();
    }

    /**
     * Reads a config from a string which is exactly like one returned from
     * the config server given only default values for this config.
     * The parsing code is the same whether the reading happens from string
     * or from a server connection, so this tests that this config can be
     * received correctly from the server
     */
    @Test
    public void testDefaultConfig() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        ConfigHandle<DefaulttestConfig> h = subscriber.subscribe(DefaulttestConfig.class, CONFIG_ID);
        assertTrue(subscriber.nextConfig(false));
        DefaulttestConfig config = h.getConfig();
        verifyConfigValues(config);
        subscriber.close();
    }

    private static void verifyConfigValues(DefaulttestConfig config) {
        assertEquals("####-------missing--------", config.nondefaultstring());
        assertEquals("thedefault", config.defaultstring());
        assertEquals("####-------missing--------", config.nondefaultreference());
        assertEquals("thedefault", config.defaultreference());
    }

}
