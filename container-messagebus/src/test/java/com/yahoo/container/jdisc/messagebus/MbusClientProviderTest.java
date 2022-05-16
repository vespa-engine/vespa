// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.messagebus.network.NetworkMultiplexer;
import com.yahoo.messagebus.shared.NullNetwork;
import com.yahoo.vespa.config.content.DistributionConfig;
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
        SessionCache cache = new SessionCache(() -> NetworkMultiplexer.dedicated(new NullNetwork()),
                                              new ContainerMbusConfig.Builder().build(),
                                              new DocumentTypeManager(new DocumentmanagerConfig.Builder().build()),
                                              new MessagebusConfig.Builder().build(),
                                              new DocumentProtocolPoliciesConfig.Builder().build(),
                                              new DistributionConfig.Builder().build());
        MbusClientProvider p = new MbusClientProvider(cache, config);
        assertNotNull(p.get());
        p.deconstruct();
    }

}
