// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.StatisticsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.metrics.yamasconsumer.cloud.ScoreBoardConfig;
import com.yahoo.net.HostName;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.xml.ConfigServerContainerModelBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ConfigserverClusterTest {

    private AbstractConfigProducerRoot root;

    @Before
    public void setupCluster() {
        String services = "<jdisc id='standalone' version='1.0'>"
                        + "  <http>"
                        + "    <server port='1337' id='configserver' />"
                        + "  </http>"
                        + "</jdisc>";
        root = new MockRoot();
        new ConfigServerContainerModelBuilder(new TestOptions().rpcPort(12345).useVespaVersionInRequest(true)
                                                               .hostedVespa(true).environment("test").region("bar")
                                                               .numParallelTenantLoaders(99))
                .build(new DeployState.Builder().build(true), null, root, XML.getDocument(services).getDocumentElement());
        root.freezeModelTopology();
    }

    @Test
    public void testStatisticsConfig() {
        StatisticsConfig config = root.getConfig(StatisticsConfig.class, "configserver/standalone");
        assertThat((int) config.collectionintervalsec(), is(60));
        assertThat((int) config.loggingintervalsec(), is(60));
    }

    @Test
    public void testScoreBoardConfig() {
        ScoreBoardConfig config = root.getConfig(ScoreBoardConfig.class, "configserver/standalone");
        assertThat(config.applicationName(), is("configserver"));
        assertThat(config.flushTime(), is(60));
        assertThat(config.step(), is(60));
    }

    @Test
    public void testHealthMonitorConfig() {
        HealthMonitorConfig config = root.getConfig(HealthMonitorConfig.class, "configserver/standalone");
        assertThat(((int) config.snapshot_interval()), is(60));
    }

    @Test
    public void testConfigserverConfig() {
        ConfigserverConfig config = root.getConfig(ConfigserverConfig.class, "configserver/standalone");
        assertThat(config.configModelPluginDir().size(), is(1));
        assertThat(config.configModelPluginDir().get(0), is(Defaults.getDefaults().underVespaHome("lib/jars/config-models")));
        assertThat(config.rpcport(), is(12345));
        assertThat(config.httpport(), is(1337));
        assertThat(config.serverId(), is(HostName.getLocalhost()));
        assertTrue(config.useVespaVersionInRequest());
        assertThat(config.numParallelTenantLoaders(), is(99));
        assertFalse(config.multitenant());
        assertTrue(config.hostedVespa());
        assertThat(config.environment(), is("test"));
        assertThat(config.region(), is("bar"));
    }

}
