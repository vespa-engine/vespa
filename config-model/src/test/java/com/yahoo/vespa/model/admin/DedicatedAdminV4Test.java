// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.LogforwarderConfig;
import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.config.model.api.container.ContainerServiceType.LOGSERVER_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static com.yahoo.config.model.api.container.ContainerServiceType.CONTAINER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class DedicatedAdminV4Test {

    private static final String hosts = "<hosts>"
            + "  <host name=\"myhost0\">"
            + "    <alias>node0</alias>"
            + "  </host>"
            + "  <host name=\"myhost1\">"
            + "    <alias>node1</alias>"
            + "  </host>"
            + "  <host name=\"myhost2\">"
            + "    <alias>node2</alias>"
            + "  </host>"
            + "</hosts>";

    @Test
    void testModelBuilding() throws IOException, SAXException {
        String services = "<services>" +
                "  <admin version='4.0'>" +
                "    <slobroks><nodes count='2' dedicated='true'/></slobroks>" +
                "    <logservers><nodes count='1' dedicated='true'/></logservers>" +
                "    <monitoring systemname='vespa.routing' interval='60' />" +
                "    <metrics>" +
                "     <consumer id='slingstone'>" +
                "        <metric id='foobar.count' display-name='foobar'/>" +
                "     </consumer>" +
                "    </metrics>" +
                "    <identity>" +
                "        <domain>mydomain</domain>" +
                "        <service>myservice</service>" +
                "    </identity>" +
                "  </admin>" +
                "</services>";

        VespaModel model = createModel(hosts, services);
        assertEquals(3, model.getHosts().size());

        assertHostContainsServices(model, "hosts/myhost0", "slobrok", "logd",
                METRICS_PROXY_CONTAINER.serviceName);
        assertHostContainsServices(model, "hosts/myhost1", "slobrok", "logd",
                METRICS_PROXY_CONTAINER.serviceName);
        // Note: A logserver container is always added on logserver host
        assertHostContainsServices(model, "hosts/myhost2", "logserver", "logd",
                METRICS_PROXY_CONTAINER.serviceName, LOGSERVER_CONTAINER.serviceName);

        Monitoring monitoring = model.getAdmin().getMonitoring();
        assertEquals("vespa.routing", monitoring.getClustername());
        assertEquals(60L, (long) monitoring.getIntervalSeconds());

        MetricsConsumer consumer = model.getAdmin().getUserMetrics().getConsumers().get("slingstone");
        assertNotNull(consumer);
        Metric metric = consumer.metrics().get("foobar.count");
        assertNotNull(metric);
        assertEquals("foobar", metric.outputName);
    }

    @Test
    void testThatThereAre2SlobroksPerContainerCluster() throws IOException, SAXException {
        String hosts = "<hosts>"
                + "  <host name=\"myhost0\">"
                + "    <alias>node0</alias>"
                + "  </host>"
                + "  <host name=\"myhost1\">"
                + "    <alias>node1</alias>"
                + "  </host>"
                + "  <host name=\"myhost2\">"
                + "    <alias>node2</alias>"
                + "  </host>"
                + "  <host name=\"myhost3\">"
                + "    <alias>node3</alias>"
                + "  </host>"
                + "</hosts>";

        String servicesWith3JdiscClusters = "<services>" +
                "  <admin version='4.0'>" +
                "    <nodes count='1' dedicated='true' />" +
                "  </admin>" +
                "  <container id='a' version='1.0'>" +
                "    <search />" +
                "    <nodes count='2' dedicated='true' />" +
                "  </container>" +
                "  <container id='b' version='1.0'>" +
                "    <search />" +
                "    <nodes count='1' dedicated='true' />" +
                "  </container>" +
                "  <container id='c' version='1.0'>" +
                "    <search />" +
                "    <nodes count='1' dedicated='true' />" +
                "  </container>" +
                "</services>";

        VespaModel model = createModel(hosts, servicesWith3JdiscClusters);
        assertEquals(4, model.getHosts().size());

        // 4 slobroks, 2 per cluster where possible
        assertHostContainsServices(model, "hosts/myhost0", "slobrok", "logd", "logserver",
                METRICS_PROXY_CONTAINER.serviceName, CONTAINER.serviceName);
        assertHostContainsServices(model, "hosts/myhost1", "slobrok", "logd",
                METRICS_PROXY_CONTAINER.serviceName, CONTAINER.serviceName);
        assertHostContainsServices(model, "hosts/myhost2", "slobrok", "logd",
                METRICS_PROXY_CONTAINER.serviceName, CONTAINER.serviceName);
        assertHostContainsServices(model, "hosts/myhost3", "slobrok", "logd",
                METRICS_PROXY_CONTAINER.serviceName, CONTAINER.serviceName);
    }

    @Test
    void testLogForwarding() throws IOException, SAXException {
        String services = "<services>" +
                "  <admin version='4.0'>" +
                "    <slobroks><nodes count='2' dedicated='true'/></slobroks>" +
                "    <logservers><nodes count='1' dedicated='true'/></logservers>" +
                "    <logforwarding include-admin='true'>" +
                "      <splunk deployment-server='foo:123' client-name='foocli' phone-home-interval='900' role='athenz://some-domain/role/role-name'/>" +
                "    </logforwarding>" +
                "  </admin>" +
                "</services>";

        VespaModel model = createModel(hosts, services);
        assertEquals(3, model.getHosts().size());

        assertHostContainsServices(model, "hosts/myhost0", "logd", "logforwarder", "slobrok",
                METRICS_PROXY_CONTAINER.serviceName);
        assertHostContainsServices(model, "hosts/myhost1", "logd", "logforwarder", "slobrok",
                METRICS_PROXY_CONTAINER.serviceName);
        // Note: A logserver container is always added on logserver host
        assertHostContainsServices(model, "hosts/myhost2", "logd", "logforwarder", "logserver",
                METRICS_PROXY_CONTAINER.serviceName, LOGSERVER_CONTAINER.serviceName);

        Set<String> configIds = model.getConfigIds();
        // 1 logforwarder on each host
        IntStream.of(0, 1, 2).forEach(i -> assertTrue(configIds.contains("hosts/myhost" + i + "/logforwarder"), configIds.toString()));

        // First forwarder
        {
            LogforwarderConfig.Builder builder = new LogforwarderConfig.Builder();
            model.getConfig(builder, "hosts/myhost0/logforwarder");
            LogforwarderConfig config = new LogforwarderConfig(builder);

            assertEquals("foo:123", config.deploymentServer());
            assertEquals("foocli", config.clientName());
            assertEquals("/opt/splunkforwarder", config.splunkHome());
            assertEquals(900, config.phoneHomeInterval());
            assertEquals("some-domain:role.role-name", config.role());
        }

        // Other host's forwarder
        {
            LogforwarderConfig.Builder builder = new LogforwarderConfig.Builder();
            model.getConfig(builder, "hosts/myhost2/logforwarder");
            LogforwarderConfig config = new LogforwarderConfig(builder);

            assertEquals("foo:123", config.deploymentServer());
            assertEquals("foocli", config.clientName());
            assertEquals("/opt/splunkforwarder", config.splunkHome());
            assertEquals(900, config.phoneHomeInterval());
            assertEquals("some-domain:role.role-name", config.role());
        }
    }

    @Test
    void testDedicatedLogserverInHostedVespa() throws IOException, SAXException {
        String services = "<services>" +
                "  <admin version='4.0'>" +
                "    <logservers>" +
                "      <nodes count='1' dedicated='true'/>" +
                "    </logservers>" +
                "  </admin>" +
                "</services>";

        VespaModel model = createModel(hosts, services, new DeployState.Builder()
                .zone(new Zone(SystemName.cd, Environment.dev, RegionName.defaultName()))
                .properties(new TestProperties().setHostedVespa(true)));
        assertEquals(1, model.getHosts().size());
        // Should create a logserver container on the same node as logserver
        assertHostContainsServices(model, "hosts/myhost0", "slobrok", "logd", "logserver",
                METRICS_PROXY_CONTAINER.serviceName, LOGSERVER_CONTAINER.serviceName);
    }

    private Set<String> serviceNames(VespaModel model, String hostname) {
        SentinelConfig config = model.getConfig(SentinelConfig.class, hostname);
        return config.service().stream().map(SentinelConfig.Service::name).collect(Collectors.toSet());
    }

    private void assertHostContainsServices(VespaModel model, String hostname, String... expectedServices) {
        Set<String> serviceNames = serviceNames(model, hostname);
        assertEquals(expectedServices.length, serviceNames.size());
        for (String serviceName : expectedServices) {
            assertTrue(serviceNames.contains(serviceName));
        }
    }

    private VespaModel createModel(String hosts, String services) throws IOException, SAXException {
        return createModel(hosts, services, new DeployState.Builder());
    }

    private VespaModel createModel(String hosts, String services, DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .build();
        return new VespaModel(new NullConfigModelRegistry(), deployStateBuilder
                .applicationPackage(app)
                .modelHostProvisioner(new InMemoryProvisioner(Hosts.readFrom(app.getHosts()), true, false))
                .build());
    }

}
