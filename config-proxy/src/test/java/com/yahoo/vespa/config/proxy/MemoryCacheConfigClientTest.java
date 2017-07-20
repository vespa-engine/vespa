// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.Test;

import java.util.Collections;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.9
 */
public class MemoryCacheConfigClientTest {

    @Test
    public void basic() {
        MemoryCache cache = new MemoryCache();
        cache.put(ConfigTester.fooConfig);
        MemoryCacheConfigClient client = new MemoryCacheConfigClient(cache);
        assertThat(client.getConfig(ConfigTester.fooConfig, null), is(ConfigTester.fooConfig));
        assertNull(client.getConfig(ConfigTester.barConfig, null));

        assertThat(client.getActiveSourceConnection(), is("N/A"));
        assertThat(client.getSourceConnections(), is(Collections.singletonList("N/A")));
    }
}
