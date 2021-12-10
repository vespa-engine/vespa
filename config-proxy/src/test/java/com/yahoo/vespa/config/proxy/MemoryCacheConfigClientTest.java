// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class MemoryCacheConfigClientTest {

    @Test
    public void basic() {
        MemoryCacheConfigClient client = new MemoryCacheConfigClient();
        client.memoryCache().update(ConfigTester.fooConfig);
        assertThat(client.getConfig(ConfigTester.fooConfig, null), is(ConfigTester.fooConfig));
        assertNull(client.getConfig(ConfigTester.barConfig, null));

        assertThat(client.getActiveSourceConnection(), is("N/A"));
        assertThat(client.getSourceConnections(), is(Collections.singletonList("N/A")));
    }

}
