// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.myproject.config.NamespaceConfig;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class NamespaceTest {

    @Test
    public void verifyConfigClassWithExplicitNamespace() {
        NamespaceConfig config = new ConfigGetter<>(NamespaceConfig.class).getConfig("raw: a 0\n");
        assertThat(config.a(), is(0));
    }
}
