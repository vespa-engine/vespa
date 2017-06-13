// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

import com.yahoo.foo.AppConfig;

import org.junit.Test;


public class BasicTest {

    @Test
    public void testSubBasic() {
        ConfigSubscriber s = new ConfigSubscriber();
        ConfigHandle<AppConfig> h = s.subscribe(AppConfig.class, "raw:times 0");
        s.nextConfig(0);
        AppConfig c = h.getConfig();
        assertThat(c.times(), is(0));
    }

    @Test
    public void testSubBasicGeneration() {
        ConfigSubscriber s = new ConfigSubscriber();
        ConfigHandle<AppConfig> h = s.subscribe(AppConfig.class, "raw:times 2");
        s.nextGeneration(0);
        AppConfig c = h.getConfig();
        assertThat(c.times(), is(2));
    }
}
