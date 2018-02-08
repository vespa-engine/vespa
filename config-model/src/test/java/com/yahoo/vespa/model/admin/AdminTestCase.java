// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.SlobroksConfig.Slobrok;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.StatisticsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.StatisticsComponent;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


/**
 * @author gjoranv
 */
public class AdminTestCase {

    protected static final String TESTDIR = "src/test/cfg/admin/";

    protected VespaModel getVespaModel(String configPath) {
        return new VespaModelCreatorWithFilePkg(configPath).create();
    }

    /**
     * Test that version 2.0 of adminconfig works as expected.
     */
    @Test
    public void testAdmin20() throws Exception {
        VespaModel vespaModel = getVespaModel(TESTDIR + "adminconfig20");

        // Verify that the admin plugin has been loaded (always loads routing).
        assertThat(vespaModel.configModelRepo().asMap().size(), is(2));

        ApplicationConfigProducerRoot root = vespaModel.getVespa();
        assertNotNull(root);

        // Verify configIds
        Set<String> configIds = vespaModel.getConfigIds();
        String localhost = HostName.getLocalhost();
        String localhostConfigId = "hosts/" + localhost;
        assertTrue(configIds.contains(localhostConfigId));
        assertTrue(configIds.contains("admin/logserver"));
        assertTrue(configIds.contains("admin/configservers/configserver.0"));
        assertTrue(configIds.contains("admin/slobrok.0"));
        assertTrue(configIds.contains("admin/slobrok.1"));
        assertFalse(configIds.contains("admin/slobrok.2"));
        assertTrue(configIds.contains("admin"));

        // Confirm 2 slobroks in config
        SlobroksConfig.Builder sb = new SlobroksConfig.Builder();
        vespaModel.getConfig(sb, "admin/slobrok.0");
        SlobroksConfig sc = new SlobroksConfig(sb);
        assertEquals(sc.slobrok().size(), 2);
        boolean localHostOK = false;
        for (Slobrok s : sc.slobrok()) {
            if (s.connectionspec().matches(".*" + localhost + ".*")) localHostOK = true;
        }
        assertTrue(localHostOK);

        LogdConfig.Builder lb = new LogdConfig.Builder();
        vespaModel.getConfig(lb, "admin/slobrok.0");
        LogdConfig lc = new LogdConfig(lb);
        assertEquals(lc.logserver().host(), localhost);

        // Verify services in the sentinel config
        SentinelConfig.Builder b = new SentinelConfig.Builder();
        vespaModel.getConfig(b, localhostConfigId);
        SentinelConfig sentinelConfig = new SentinelConfig(b);
        assertThat(sentinelConfig.service().size(), is(4));
        assertThat(sentinelConfig.service(0).name(), is("logserver"));
        assertThat(sentinelConfig.service(1).name(), is("slobrok"));
        assertThat(sentinelConfig.service(2).name(), is("slobrok2"));
        assertThat(sentinelConfig.service(3).name(), is("logd"));
    }

    /**
     * Test that a very simple config with only adminserver tag creates
     * adminserver, logserver, configserver and slobroks
     */
    @Test
    public void testOnlyAdminserver() throws Exception {
        VespaModel vespaModel = getVespaModel(TESTDIR + "simpleadminconfig20");

        // Verify that the admin plugin has been loaded (always loads routing).
        assertThat(vespaModel.configModelRepo().asMap().size(), is(2));

        ApplicationConfigProducerRoot root = vespaModel.getVespa();
        assertNotNull(root);

        // Verify configIds
        Set<String> configIds = vespaModel.getConfigIds();
        String localhost = HostName.getLocalhost();
        String localhostConfigId = "hosts/" + localhost;
        assertTrue(configIds.contains(localhostConfigId));
        assertTrue(configIds.contains("admin/logserver"));
        assertTrue(configIds.contains("admin/configservers/configserver.0"));
        assertTrue(configIds.contains("admin/slobrok.0"));
        assertFalse(configIds.contains("admin/slobrok.1"));

        // Verify services in the sentinel config
        SentinelConfig.Builder b = new SentinelConfig.Builder();
        vespaModel.getConfig(b, localhostConfigId);
        SentinelConfig sentinelConfig = new SentinelConfig(b);
        assertThat(sentinelConfig.service().size(), is(3));
        assertThat(sentinelConfig.service(0).name(), is("logserver"));
        assertThat(sentinelConfig.service(1).name(), is("slobrok"));
        assertThat(sentinelConfig.service(2).name(), is("logd"));
        assertThat(sentinelConfig.service(0).affinity().cpuSocket(), is(-1));
        assertTrue(sentinelConfig.service(0).preShutdownCommand().isEmpty());

        // Confirm slobrok config
        SlobroksConfig.Builder sb = new SlobroksConfig.Builder();
        vespaModel.getConfig(sb, "admin");
        SlobroksConfig sc = new SlobroksConfig(sb);
        assertEquals(sc.slobrok().size(), 1);
        assertTrue(sc.slobrok().get(0).connectionspec().matches(".*" + localhost + ".*"));
    }

    @Test
    public void testTenantAndAppInSentinelConfig() {
        DeployState state = new DeployState.Builder().properties(
                new DeployProperties.Builder().
                        zone(new Zone(Environment.dev, RegionName.from("baz"))).
                applicationId(new ApplicationId.Builder().
                        tenant("quux").
                        applicationName("foo").instanceName("bim").build()).build()).build();
        TestRoot root = new TestDriver().buildModel(state);
        String localhost = HostName.getLocalhost();
        SentinelConfig config = root.getConfig(SentinelConfig.class, "hosts/" + localhost);
        assertThat(config.application().tenant(), is("quux"));
        assertThat(config.application().name(), is("foo"));
        assertThat(config.application().environment(), is("dev"));
        assertThat(config.application().region(), is("baz"));
        assertThat(config.application().instance(), is("bim"));
    }

    @Test
    public void testMultipleConfigServers() throws Exception {
        VespaModel vespaModel = getVespaModel(TESTDIR + "multipleconfigservers");

        // Verify that the admin plugin has been loaded (always loads routing).
        assertThat(vespaModel.configModelRepo().asMap().size(), is(2));
        ApplicationConfigProducerRoot root = vespaModel.getVespa();
        assertNotNull(root);

        Admin admin = vespaModel.getAdmin();
        assertNotNull(admin);

        // Verify configIds
        Set<String> configIds = vespaModel.getConfigIds();
        String localhost = HostName.getLocalhost();
        String localhostConfigId = "hosts/" + localhost;
        assertTrue(configIds.contains(localhostConfigId));
        assertTrue(configIds.contains("admin/logserver"));
        assertTrue(configIds.contains("admin/configservers/configserver.0"));
        assertTrue(configIds.contains("admin/configservers/configserver.1"));

        assertThat(admin.getConfigservers().size(), is(2));

        // Default configserver is the first one in the list and should have the default ports too
        Configserver server1 = admin.getConfigservers().get(0);
        assertEquals(admin.getConfigserver(), server1);
        assertThat(server1.getPortCount(), is(2));
        assertThat(server1.getRelativePort(0), is(19070));
        assertThat(server1.getRelativePort(1), is(19071));


        // Second configserver should be on second host but have the same port number
        Configserver server2 = admin.getConfigservers().get(1);

        assertNotSame(server1, server2);
        assertNotSame(server1.getHostName(), server2.getHostName());

        assertThat(server2.getPortCount(), is(2));
        assertThat(server2.getRelativePort(0), is(19070));
        assertThat(server2.getRelativePort(1), is(19071));
    }

    @Test
    public void testContainerMetricsSnapshotInterval() throws Exception {
        VespaModel vespaModel = getVespaModel(TESTDIR + "metricconfig");

        ContainerCluster docprocCluster = vespaModel.getContainerClusters().get("cluster.music.indexing");
        HealthMonitorConfig.Builder builder = new HealthMonitorConfig.Builder();
        docprocCluster.getConfig(builder);
        HealthMonitorConfig docprocConfig = new HealthMonitorConfig(builder);
        assertEquals(60, (int) docprocConfig.snapshot_interval());

        ContainerCluster qrCluster = vespaModel.getContainerClusters().get("container");
        builder = new HealthMonitorConfig.Builder();
        qrCluster.getConfig(builder);
        HealthMonitorConfig qrClusterConfig = new HealthMonitorConfig(builder);
        assertEquals(60, (int) qrClusterConfig.snapshot_interval());

        StatisticsComponent stat = null;
        for (Component component : qrCluster.getAllComponents()) {
            System.out.println(component.getClassId().getName());
            if (component.getClassId().getName().contains("com.yahoo.statistics.StatisticsImpl")) {
                stat = (StatisticsComponent) component;
                break;
            }
        }
        assertNotNull(stat);
        StatisticsConfig.Builder sb = new StatisticsConfig.Builder();
        stat.getConfig(sb);
        StatisticsConfig sc = new StatisticsConfig(sb);
        assertEquals(60, (int) sc.collectionintervalsec());
        assertEquals(60, (int) sc.loggingintervalsec());
    }

    @Test
    public void testStatisticsConfig() {
        StatisticsComponent stat = new StatisticsComponent();
        StatisticsConfig.Builder sb = new StatisticsConfig.Builder();
        stat.getConfig(sb);
        StatisticsConfig sc = new StatisticsConfig(sb);
        assertEquals(sc.collectionintervalsec(), 300, 0.1);
        assertEquals(sc.loggingintervalsec(), 300, 0.1);
        assertEquals(sc.values(0).operations(0).name(), StatisticsConfig.Values.Operations.Name.REGULAR);
        assertEquals(sc.values(0).operations(0).arguments(0).key(), "limits");
        assertEquals(sc.values(0).operations(0).arguments(0).value(), "25,50,100,500");
    }

    @Test
    public void testLogForwarding() throws Exception {
        String hosts = "<hosts>"
                + "  <host name=\"myhost0\">"
                + "    <alias>node0</alias>"
                + "  </host>"
                + "</hosts>";

        String services = "<services>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0' />" +
                "    <logforwarding>" +
                "      <splunk deployment-server='foo:123' client-name='foocli'/>" +
                "    </logforwarding>" +
                "  </admin>" +
                "</services>";

        VespaModel vespaModel = new VespaModelCreatorWithMockPkg(hosts, services).create();

        Set<String> configIds = vespaModel.getConfigIds();
        // 1 logforwarder on each host
        assertTrue(configIds.toString(), configIds.contains("hosts/myhost0/logforwarder"));
    }

    @Test
    public void disableFiledistributorService() throws Exception {
        String hosts = "<hosts>"
                + "  <host name=\"localhost\">"
                + "    <alias>node0</alias>"
                + "  </host>"
                + "</hosts>";

        String services = "<services>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node0' />" +
                "    <filedistribution>" +
                "      <disableFiledistributor>true</disableFiledistributor>" +
                "    </filedistribution>" +
                "  </admin>" +
                "</services>";

        VespaModel vespaModel = new VespaModelCreatorWithMockPkg(hosts, services).create();
        String localhost = HostName.getLocalhost();
        String localhostConfigId = "hosts/" + localhost;

        // Verify services in the sentinel config
        SentinelConfig.Builder b = new SentinelConfig.Builder();
        vespaModel.getConfig(b, localhostConfigId);
        SentinelConfig sentinelConfig = new SentinelConfig(b);
        assertThat(sentinelConfig.service().size(), is(3));
        assertThat(sentinelConfig.service(0).name(), is("logserver"));
        assertThat(sentinelConfig.service(1).name(), is("slobrok"));
        assertThat(sentinelConfig.service(2).name(), is("logd"));
        // No filedistributor service
    }

    @Test
    public void testDisableFileDistributorForAllApps() {
        DeployState state = new DeployState.Builder()
                .disableFiledistributor(true)
                .properties(
                        new DeployProperties.Builder().
                                zone(new Zone(Environment.dev, RegionName.from("baz"))).
                                applicationId(new ApplicationId.Builder().
                                        tenant("quux").
                                        applicationName("foo").instanceName("bim").build()).build()).build();
        TestRoot root = new TestDriver().buildModel(state);
        String localhost = HostName.getLocalhost();
        SentinelConfig sentinelConfig = root.getConfig(SentinelConfig.class, "hosts/" + localhost);
        assertThat(sentinelConfig.service().size(), is(3));
        assertThat(sentinelConfig.service(0).name(), is("logserver"));
        assertThat(sentinelConfig.service(1).name(), is("slobrok"));
        assertThat(sentinelConfig.service(2).name(), is("logd"));
    }

}
