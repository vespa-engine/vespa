// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.container.StatisticsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.net.HostName;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import com.yahoo.vespa.model.container.xml.ConfigServerContainerModelBuilder;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ConfigserverClusterTest {

    @Test
    public void zookeeperConfig_default() {
        ZookeeperServerConfig config = getConfig(ZookeeperServerConfig.class);
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::hostname, "localhost");
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::id, 0);
        assertEquals(0, config.myid());
    }

    @Test
    public void zookeeperConfig_only_config_servers_set() {
        TestOptions testOptions = createTestOptions(Arrays.asList("cfg1", "localhost", "cfg3"), Collections.emptyList());
        ZookeeperServerConfig config = getConfig(ZookeeperServerConfig.class, testOptions);
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::hostname, "cfg1", "localhost", "cfg3");
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::id, 0, 1, 2);
        assertEquals(1, config.myid());
    }

    @Test
    public void zookeeperConfig_with_config_servers_and_zk_ids() {
        TestOptions testOptions = createTestOptions(Arrays.asList("cfg1", "localhost", "cfg3"), Arrays.asList(4, 2, 3));
        ZookeeperServerConfig config = getConfig(ZookeeperServerConfig.class, testOptions);
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::hostname, "cfg1", "localhost", "cfg3");
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::id, 4, 2, 3);
        assertEquals(2, config.myid());
    }

    @Test
    public void zookeeperConfig_self_hosted() {
        final boolean hostedVespa = false;
        TestOptions testOptions = createTestOptions(Arrays.asList("cfg1", "localhost", "cfg3"), Arrays.asList(4, 2, 3), hostedVespa);
        ZookeeperServerConfig config = getConfig(ZookeeperServerConfig.class, testOptions);
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::hostname, "cfg1", "localhost", "cfg3");
        assertZookeeperServerProperty(config.server(), ZookeeperServerConfig.Server::id, 4, 2, 3);
        assertEquals(2, config.myid());
    }

    @Test(expected = IllegalArgumentException.class)
    public void zookeeperConfig_uneven_number_of_config_servers_and_zk_ids() {
        TestOptions testOptions = createTestOptions(Arrays.asList("cfg1", "localhost", "cfg3"), Collections.singletonList(1));
        getConfig(ZookeeperServerConfig.class, testOptions);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zookeeperConfig_negative_zk_id() {
        TestOptions testOptions = createTestOptions(Arrays.asList("cfg1", "localhost", "cfg3"), Arrays.asList(1, 2, -1));
        getConfig(ZookeeperServerConfig.class, testOptions);
    }

    @Test
    public void testStatisticsConfig() {
        StatisticsConfig config = getConfig(StatisticsConfig.class);
        assertThat((int) config.collectionintervalsec(), is(60));
        assertThat((int) config.loggingintervalsec(), is(60));
    }

    @Test
    public void testHealthMonitorConfig() {
        HealthMonitorConfig config = getConfig(HealthMonitorConfig.class);
        assertThat(((int) config.snapshot_interval()), is(60));
    }

    @Test
    public void testConfigserverConfig() {
        ConfigserverConfig config = getConfig(ConfigserverConfig.class);
        assertThat(config.configModelPluginDir().size(), is(1));
        assertThat(config.configModelPluginDir().get(0), is(Defaults.getDefaults().underVespaHome("lib/jars/config-models")));
        assertThat(config.rpcport(), is(12345));
        assertThat(config.httpport(), is(1337));
        assertThat(config.serverId(), is(HostName.getLocalhost()));
        assertTrue(config.useVespaVersionInRequest());
        assertThat(config.numParallelTenantLoaders(), is(4));
        assertFalse(config.multitenant());
        assertTrue(config.hostedVespa());
        assertThat(config.environment(), is("test"));
        assertThat(config.region(), is("bar"));
    }

    @Test
    public void testCuratorConfig() {
        CuratorConfig config = getConfig(CuratorConfig.class);
        assertEquals(1, config.server().size());
        assertEquals("localhost", config.server().get(0).hostname());
        assertEquals(2181, config.server().get(0).port());
        assertTrue(config.zookeeperLocalhostAffinity());
    }

    @SuppressWarnings("varargs")
    private static <T> void assertZookeeperServerProperty(
            List<ZookeeperServerConfig.Server> zkServers, Function<ZookeeperServerConfig.Server, T> propertyMapper, T... expectedProperties) {
        List<T> actualPropertyValues = zkServers.stream().map(propertyMapper).collect(Collectors.toList());
        List<T> expectedPropertyValues = Arrays.asList(expectedProperties);
        assertEquals(expectedPropertyValues, actualPropertyValues);
    }

    private static TestOptions createTestOptions(List<String> configServerHostnames, List<Integer> configServerZkIds) {
        return createTestOptions(configServerHostnames, configServerZkIds, true);

    }
    private static TestOptions createTestOptions(List<String> configServerHostnames, List<Integer> configServerZkIds, boolean hostedVespa) {
        TestOptions testOptions = new TestOptions()
                .rpcPort(12345)
                .useVespaVersionInRequest(true)
                .hostedVespa(hostedVespa)
                .environment("test")
                .region("bar");

        Optional.of(configServerHostnames)
                .filter(hostnames -> !hostnames.isEmpty())
                .map(hostnames -> hostnames.stream()
                        .map(hostname -> new CloudConfigOptions.ConfigServer(hostname, Optional.empty()))
                        .toArray(CloudConfigOptions.ConfigServer[]::new))
                .ifPresent(testOptions::configServers);

        Optional.of(configServerZkIds)
                .filter(zkIds -> !zkIds.isEmpty())
                .map(zkIds -> zkIds.stream().mapToInt(i -> i).toArray())
                .ifPresent(testOptions::configServerZookeeperIds);

        return testOptions;
    }

    private static <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> clazz) {
        return getConfig(clazz, createTestOptions(Collections.emptyList(), Collections.emptyList()));
    }

    private static <CONFIGTYPE extends ConfigInstance> CONFIGTYPE getConfig(Class<CONFIGTYPE> clazz, TestOptions testOptions) {
        AbstractConfigProducerRoot root = new MockRoot();
        String services = "<container id='standalone' version='1.0'>"
                + "  <http>"
                + "    <server port='1337' id='configserver' />"
                + "  </http>"
                + "</container>";
        ContainerModel containerModel = new ConfigServerContainerModelBuilder(testOptions)
                .build(new DeployState.Builder().build(), null, null, root, XML.getDocument(services).getDocumentElement());

        // Simulate the behaviour of StandaloneContainer
        List<? extends Container> containers = containerModel.getCluster().getContainers();
        assertEquals("Standalone container", 1, containers.size());
        HostResource hostResource = root.hostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC);
        containers.get(0).setHostResource(hostResource);

        root.freezeModelTopology();
        return root.getConfig(clazz, "configserver/standalone");
    }

}
