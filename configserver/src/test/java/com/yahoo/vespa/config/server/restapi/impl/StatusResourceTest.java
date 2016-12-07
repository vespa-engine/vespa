// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.restapi.impl;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.restapi.resources.StatusInformation;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class StatusResourceTest {
    @Test
    public void require_that_status_handler_responds_to_ping() throws IOException {
        StatusResource handler = new StatusResource(null, null, null, null, null, null, null, new TestComponentRegistry.Builder().build());
        assertNotNull(handler.getStatus().configserverConfig);
    }

    @Test
    public void require_that_generated_config_is_converted() {
        ConfigserverConfig orig = new ConfigserverConfig(new ConfigserverConfig.Builder());
        StatusInformation.ConfigserverConfig conv = new StatusInformation.ConfigserverConfig(orig);
        assertThat(conv.applicationDirectory, is(Defaults.getDefaults().underVespaHome(orig.applicationDirectory())));
        assertThat(conv.configModelPluginDir.size(), is(orig.configModelPluginDir().size()));
        assertThat(conv.zookeeeperserver.size(), is(orig.zookeeperserver().size()));
        assertThat(conv.zookeeperBarrierTimeout, is(orig.zookeeper().barrierTimeout()));
        assertThat(conv.configServerDBDir, is(Defaults.getDefaults().underVespaHome(orig.configServerDBDir())));
        assertThat(conv.masterGeneration, is(orig.masterGeneration()));
        assertThat(conv.maxgetconfigclients, is(orig.maxgetconfigclients()));
        assertThat(conv.multitenant, is(orig.multitenant()));
        assertThat(conv.numDelayedResponseThreads, is(orig.numDelayedResponseThreads()));
        assertThat(conv.numthreads, is(orig.numthreads()));
        assertThat(conv.payloadCompressionType, is(orig.payloadCompressionType()));
        assertThat(conv.rpcport, is(orig.rpcport()));
        assertThat(conv.sessionLifetime, is(orig.sessionLifetime()));
        assertThat(conv.zookeepercfg, is(Defaults.getDefaults().underVespaHome(orig.zookeepercfg())));
    }
}
