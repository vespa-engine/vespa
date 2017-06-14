// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class ConfigHelperTest {

    @Test
    public void basic() {
        ConfigSourceSet configSourceSet = new ConfigSourceSet("host.com");
        ConfigHelper helper = new ConfigHelper(configSourceSet);
        assertThat(helper.getConfigSourceSet(), is(configSourceSet));
        assertThat(helper.getConnectionPool().getAllSourceAddresses(), is("host.com"));
        assertThat(helper.getTimingValues().getSubscribeTimeout(), is(new TimingValues().getSubscribeTimeout()));

        // Specify timing values
        TimingValues tv = new TimingValues();
        tv.setSubscribeTimeout(11L);
        helper = new ConfigHelper(configSourceSet, tv);
        assertThat(helper.getTimingValues().getSubscribeTimeout(), is(tv.getSubscribeTimeout()));
    }

}
