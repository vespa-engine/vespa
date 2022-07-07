// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.myproject.config.NamespaceConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class NamespaceTest {

    @Test
    @SuppressWarnings("deprecation")
    public void verifyConfigClassWithExplicitNamespace() {
        NamespaceConfig config = new ConfigGetter<>(NamespaceConfig.class).getConfig("raw: a 0\n");
        assertEquals(0, config.a());
    }
}
