// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.LogforwarderConfig;
import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vespa.model.admin.monitoring.DefaultMetricsConsumer.VESPA_CONSUMER_ID;
import static org.junit.Assert.*;

/**
 * @author lulf
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
    public void testModelBuilding() throws IOException, SAXException {
        String services = "<services>" +
                "  <admin version='4.0'>" +
                "    <slobroks><nodes count='2' dedicated='true'/></slobroks>" +
                "    <logservers><nodes count='1' dedicated='true'/></logservers>" +
                "    <yamas systemname='vespa.routing' interval='60' />" +
                "    <metrics>" +
                "     <consumer id='slingstone'>" +
                "        <metric id='foobar.count' display-name='foobar'/>" +
                "     </consumer>" +
                "    </metrics>" +
                "    <metric-consumers>" +
                "      <consumer name='yamas'>" +
                "        <metric name='upstreams_generated' />" +
                "        <metric name='upstreams_nginx_reloads' />" +
                "        <metric name='nginx.upstreams.down.last' output-name='nginx.upstreams.down'/>" +
                "      </consumer>" +
                "    </metric-consumers>" +
                "    <identity>" +
                "        <domain>mydomain</domain>" +
                "        <service>myservice</service>" +
                "    </identity>" +
                "  </admin>" +
                "</services>";

        VespaModel model = createModel(hosts, services);
        assertEquals(3, model.getHosts().size());

        assertHostContainsServices(model, "hosts/myhost0",
                                   "slobrok", "logd", "filedistributorservice");
        assertHostContainsServices(model, "hosts/myhost1",
                                   "slobrok", "logd", "filedistributorservice");
        assertHostContainsServices(model, "hosts/myhost2",
                                   "logserver", "logd", "filedistributorservice");

        Monitoring monitoring = model.getAdmin().getMonitoring();
        assertEquals("vespa.routing", monitoring.getClustername());
        assertEquals(60L, (long) monitoring.getIntervalSeconds());

        MetricsConsumer consumer = model.getAdmin().getUserMetrics().getConsumers().get("slingstone");
        assertNotNull(consumer);
        Metric metric = consumer.getMetrics().get("foobar.count");
        assertNotNull(metric);
        assertEquals("foobar", metric.outputName);

        consumer = model.getAdmin().getLegacyUserMetricsConsumers().get(VESPA_CONSUMER_ID);
        assertNotNull(consumer);
        assertEquals(3, consumer.getMetrics().size());
        metric = consumer.getMetrics().get("nginx.upstreams.down.last");
        assertNotNull(metric);
        assertEquals("nginx.upstreams.down", metric.outputName);
    }

    @Test
    public void testThatThereAre2SlobroksPerContainerCluster() throws IOException, SAXException {
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
                    "  <jdisc id='a' version='1.0'>" +
                    "    <search />" +
                    "    <nodes count='2' dedicated='true' />" +
                    "  </jdisc>" +
                    "  <jdisc id='b' version='1.0'>" +
                    "    <search />" +
                    "    <nodes count='1' dedicated='true' />" +
                    "  </jdisc>" +
                    "  <jdisc id='c' version='1.0'>" +
                    "    <search />" +
                    "    <nodes count='1' dedicated='true' />" +
                    "  </jdisc>" +
                    "</services>";

        VespaModel model = createModel(hosts, servicesWith3JdiscClusters);
        assertEquals(4, model.getHosts().size());

        // 4 slobroks, 2 per cluster where possible
        assertHostContainsServices(model, "hosts/myhost0",
                                   "slobrok", "logd", "filedistributorservice", "logserver", "qrserver");
        assertHostContainsServices(model, "hosts/myhost1",
                                   "slobrok", "logd", "filedistributorservice", "qrserver");
        assertHostContainsServices(model, "hosts/myhost2",
                                   "slobrok", "logd", "filedistributorservice", "qrserver");
        assertHostContainsServices(model, "hosts/myhost3",
                                   "slobrok", "logd", "filedistributorservice", "qrserver");
    }

    @Test
    public void testLogForwarding() throws IOException, SAXException {
        String services = "<services>" +
                "  <admin version='4.0'>" +
                "    <slobroks><nodes count='2' dedicated='true'/></slobroks>" +
                "    <logservers><nodes count='1' dedicated='true'/></logservers>" +
                "    <logforwarding>" +
                "      <splunk deployment-server='foo:123' client-name='foocli'/>" +
                "    </logforwarding>" +
                "  </admin>" +
                "</services>";

        VespaModel model = createModel(hosts, services);
        assertEquals(3, model.getHosts().size());

        assertHostContainsServices(model, "hosts/myhost0",
                                   "filedistributorservice", "logd", "logforwarder", "slobrok");
        assertHostContainsServices(model, "hosts/myhost1",
                                   "filedistributorservice", "logd", "logforwarder", "slobrok");
        assertHostContainsServices(model, "hosts/myhost2",
                                   "filedistributorservice", "logd", "logforwarder", "logserver");

        Set<String> configIds = model.getConfigIds();
        // 1 logforwarder on each host
        IntStream.of(0, 1, 2).forEach(i -> assertTrue(configIds.toString(), configIds.contains("hosts/myhost"+i+"/logforwarder")));

        // First forwarder
        {
            LogforwarderConfig.Builder builder = new LogforwarderConfig.Builder();
            model.getConfig(builder, "hosts/myhost0/logforwarder");
            LogforwarderConfig config = new LogforwarderConfig(builder);

            assertEquals("foo:123", config.deploymentServer());
            assertEquals("foocli", config.clientName());
            assertEquals("/opt/splunkforwarder", config.splunkHome());
        }

        // Other host's forwarder
        {
            LogforwarderConfig.Builder builder = new LogforwarderConfig.Builder();
            model.getConfig(builder, "hosts/myhost2/logforwarder");
            LogforwarderConfig config = new LogforwarderConfig(builder);

            assertEquals("foo:123", config.deploymentServer());
            assertEquals("foocli", config.clientName());
        }
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
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(hosts)
                .withServices(services)
                .build();
        return new VespaModel(new NullConfigModelRegistry(),
                              new DeployState.Builder().applicationPackage(app).modelHostProvisioner(
                                      new InMemoryProvisioner(Hosts.readFrom(app.getHosts()), true))
                                                       .build());
    }

}
