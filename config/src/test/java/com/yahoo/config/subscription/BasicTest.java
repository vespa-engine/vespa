// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;


import com.yahoo.foo.AppConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class BasicTest {

    @Test
    public void testSubBasic() {
        ConfigSubscriber s = new ConfigSubscriber();
        ConfigHandle<AppConfig> h = s.subscribe(AppConfig.class, "raw:times 0");
        s.nextConfig(0, false);
        AppConfig c = h.getConfig();
        assertEquals(0, c.times());
        s.close();
    }

    @Test
    public void testSubBasicGeneration() {
        ConfigSubscriber s = new ConfigSubscriber();
        ConfigHandle<AppConfig> h = s.subscribe(AppConfig.class, "raw:times 2");
        s.nextGeneration(0, false);
        AppConfig c = h.getConfig();
        assertEquals(2, c.times());
        s.close();
    }
}
