// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.container.jdisc.messagebus.MbusClientProvider;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Ulf Lilleengen
 */
public class MbusClientProviderTest {

    @Test
    public void testIntermediateClient() {
        SessionConfig.Builder builder = new SessionConfig.Builder();
        builder.name("foo");
        builder.type(SessionConfig.Type.Enum.INTERMEDIATE);
        testClient(new SessionConfig(builder));
    }

    @Test
    public void testSourceClient() {
        SessionConfig.Builder builder = new SessionConfig.Builder();
        builder.name("foo");
        builder.type(SessionConfig.Type.Enum.SOURCE);
        testClient(new SessionConfig(builder));
    }

    private void testClient(SessionConfig config) {
        MbusClientProvider p = new MbusClientProvider(new SessionCache("dir:src/test/resources/config/clientprovider"), config);
        assertNotNull(p.get());
        p.deconstruct();
    }

}
