// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author hmusum
 */
public class MemoryCacheConfigClientTest {

    @Test
    public void basic() {
        MemoryCacheConfigClient client = new MemoryCacheConfigClient(new MemoryCache());
        client.memoryCache().update(ConfigTester.fooConfig);
        assertEquals(ConfigTester.fooConfig, client.getConfig(ConfigTester.fooConfig, null));
        assertNull(client.getConfig(ConfigTester.barConfig, null));

        assertEquals("N/A", client.getActiveSourceConnection());
        assertEquals(List.of("N/A"), client.getSourceConnections());
    }

}
