// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.UnknownConfigIdException;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.application.validation.Validation;
import com.yahoo.vespa.model.test.utils.CommonVespaModelSetup;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class VespaModelTestCase {

    private static final String TESTDIR = "src/test/cfg/application/";
    private static final String simpleHosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
            "<hosts>  " +
            "<host name=\"localhost\">" +
            "<alias>node0</alias>" +
            "</host>" +
            "</hosts>";

    public static VespaModel getVespaModel(String configPath) {
        return getVespaModel(configPath, true);
    }

    public static VespaModel getVespaModel(String configPath, boolean validateXml) {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(configPath);
        return creator.create(validateXml);
    }

    // Debugging
    @SuppressWarnings({"UnusedDeclaration"})
    private static void dumpTree(ConfigProducer producer) {
        Map<String, ? extends ConfigProducer> id2cp = producer.getChildren();
        for (ConfigProducer c : id2cp.values()) {
            System.out.println("id: " + c.getConfigId());
            if (c.getChildren().size() > 0) {
                dumpTree(c);
            }
        }
    }

    // Verify that common config from plugins is delivered from the root node for any configId, using the Builder based API
    @Test
    public void testCommonConfig() throws Exception {
        VespaModel model = getVespaModel(TESTDIR + "app_nohosts/");
        LogdConfig.Builder b = new LogdConfig.Builder();
        b = (LogdConfig.Builder) model.getConfig(b, "");
        LogdConfig c = new LogdConfig(b);
        assertEquals(HostName.getLocalhost(), c.logserver().host());

        SlobroksConfig.Builder sb = new SlobroksConfig.Builder();
        sb = (com.yahoo.cloud.config.SlobroksConfig.Builder) model.getConfig(sb, "");
        SlobroksConfig sbc = new SlobroksConfig(sb);
        assertEquals(sbc.slobrok().size(), 1);

        ZookeepersConfig.Builder zb = new ZookeepersConfig.Builder();
        zb = (ZookeepersConfig.Builder) model.getConfig(zb, "");
        ZookeepersConfig zc = new ZookeepersConfig(zb);
        assertEquals(zc.zookeeperserverlist().split(",").length, 2);
        assertTrue(zc.zookeeperserverlist().startsWith(HostName.getLocalhost()));

        ApplicationIdConfig.Builder appIdBuilder = new ApplicationIdConfig.Builder();
        appIdBuilder = (ApplicationIdConfig.Builder) model.getConfig(appIdBuilder, "");
        ApplicationIdConfig applicationIdConfig = new ApplicationIdConfig(appIdBuilder);
        assertEquals(ApplicationId.defaultId().tenant().value(), applicationIdConfig.tenant());
        assertEquals(ApplicationId.defaultId().application().value(), applicationIdConfig.application());
        assertEquals(ApplicationId.defaultId().instance().value(), applicationIdConfig.instance());
    }

    @Test
    public void testHostsConfig() {
        VespaModel model = getVespaModel(TESTDIR + "app_qrserverandgw");
        LogdConfig config = getLogdConfig(model, "");
        assertEquals(config.logserver().host(), HostName.getLocalhost());
        assertNotNull(config);
        config = getLogdConfig(model, "hosts");
        assertNotNull(config);
        assertEquals(config.logserver().host(), HostName.getLocalhost());
    }

    private static LogdConfig getLogdConfig(VespaModel model, String configId) {
        LogdConfig.Builder b = new LogdConfig.Builder();
        b = (LogdConfig.Builder) model.getConfig(b, configId);
        if (b == null)
            return null;
        return new LogdConfig(b);
    }

    @Test
    public void testHostsOverrides() throws IOException, SAXException {
        VespaModel model = new VespaModelCreatorWithMockPkg(
                simpleHosts,
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                        "<services  version=\"1.0\">" +
                        "<config name=\"cloud.config.log.logd\">" +
                        "<logserver><host>foo</host></logserver>" +
                        "</config>" +
                        "<admin  version=\"2.0\">" +
                        "  <adminserver hostalias=\"node0\" />" +
                        "</admin>" +
                        "</services>").create();
        LogdConfig config = getLogdConfig(model, "");
        assertNotNull(config);
        assertEquals(config.logserver().host(), "foo");
        config = getLogdConfig(model, "hosts/" + HostName.getLocalhost() + "/logd");
        assertNotNull(config);
        assertEquals(config.logserver().host(), "foo");
    }

    @Ignore
    @Test(expected = UnknownConfigIdException.class)
    public void testIllegalConfigIdWithBuilders() {
        VespaModel model = getVespaModel(TESTDIR + "app_nohosts/");
        DocumentmanagerConfig.Builder db = new DocumentmanagerConfig.Builder();
        model.getConfig(db, "bogus");
    }

    @Test
    public void testConfigLists() {
        VespaModel model = getVespaModel(TESTDIR + "app_nohosts/");
        assertTrue(model.allConfigsProduced().size() > 0);
        assertTrue(model.allConfigIds().size() > 0);
    }

    @Test
    public void testCreateFromReaders() throws SAXException, IOException {
        VespaModel model = CommonVespaModelSetup.createVespaModelWithMusic(
                simpleHosts,
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                        "<services  version=\"1.0\">" +
                        "<admin  version=\"2.0\">" +
                        "  <adminserver hostalias=\"node0\" />" +
                        "</admin>" +
                        "<container version=\"1.0\">" +
                        "   <nodes>" +
                        "      <node hostalias=\"node0\" />" +
                        "   </nodes>" +
                        "   <search/>" +
                        "   <document-api/>" +
                        "</container>" +
                        "<content id=\"music\" version=\"1.0\">" +
                        "   <redundancy>1</redundancy>" +
                        "   <nodes>" +
                        "      <node hostalias=\"node0\" distribution-key=\"0\"/>" +
                        "   </nodes>" +
                        "   <documents>" +
                        "     <document type=\"music\" mode=\"index\"/>" +
                        "   </documents>" +
                        "</content>" +
                        "</services>");
        MessagebusConfig.Builder mBusB = new MessagebusConfig.Builder();
        model.getConfig(mBusB, "client");
        MessagebusConfig mBus = new MessagebusConfig(mBusB);
        assertEquals(mBus.routingtable().size(), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostsWithoutAliases() {
        new TestDriver().buildModel(
                "<services version='1.0'>" +
                        "  <admin version='2.0'>" +
                        "    <adminserver hostalias='node0' />" +
                        "  </admin>" +
                        "</services>",
                "<hosts>" +
                        "  <host name='localhost'>" +
                        "     <alias>node0</alias>" +
                        "  </host>" +
                        "  <host name='foo.yahoo.com' />" +
                        "</hosts>");
    }

    class MyLogger implements DeployLogger {
        List<Pair<Level, String>> msgs = new ArrayList<>();
        @Override
        public void log(Level level, String message) {
            msgs.add(new Pair<>(level, message));
        }
    }
    
    @Test
    public void testDeployLogger() throws IOException, SAXException {
        final String services = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<services  version=\"1.0\">" +
                "<config name=\"unknsownfoo\">" +
                "<logserver><host>foo</host></logserver>" +
                "</config>" +
                "<admin  version=\"2.0\">" +
                "  <adminserver hostalias=\"node0\" />" +
                "</admin>" +
                "</services>";

        MyLogger logger = new MyLogger();
        final DeployState.Builder builder = new DeployState.Builder();
        builder.modelHostProvisioner(new HostsXmlProvisioner(new StringReader(simpleHosts)));
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withHosts(simpleHosts)
                .withServices(services)
                .build();
        DeployState deployState = builder.deployLogger(logger).applicationPackage(app).build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        Validation.validate(model, true, deployState);
        System.out.println(logger.msgs);
        assertFalse(logger.msgs.isEmpty());
    }

    @Test
    public void testNoAdmin() throws IOException, SAXException {
        VespaModel model = CommonVespaModelSetup.createVespaModelWithMusic(
                simpleHosts,
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                        "<services version=\"1.0\">" +
                        "</services>");
        Admin admin = model.getAdmin();
        assertThat(admin.getSlobroks().size(), is(1));
        assertThat(admin.getConfigservers().size(), is(1));
        Set<HostInfo> hosts = model.getHosts();
        assertThat(hosts.size(), is(1));
        //logd, config proxy, sentinel, config server, slobrok, log server, file distributor
        HostInfo host = hosts.iterator().next();
        assertThat(host.getServices().size(), is(6));
        new LogdConfig((LogdConfig.Builder) model.getConfig(new LogdConfig.Builder(), "admin/model"));

    }

    @Test
    public void testNoMultitenantHostExported() throws IOException, SAXException {
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices("<services version='1.0'><admin version='3.0'><nodes count='1' /></admin></services>")
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .modelHostProvisioner(new InMemoryProvisioner(true, "host1.yahoo.com"))
                .properties(new DeployProperties.Builder()
                        .configServerSpecs(Arrays.asList(new Configserver.Spec("cfghost", 1234, 1235, 1236)))
                        .multitenant(true)
                        .build())
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        AllocatedHosts info = model.allocatedHosts();
        assertEquals("Admin version 3 is ignored, and there are no other hosts to borrow for admin services", 0, info.getHosts().size());
    }

    @Test
    public void testMinimalApp() throws IOException, SAXException {
        VespaModel model = new VespaModel(new MockApplicationPackage.Builder()
                                                  .withServices("<services version='1.0'><jdisc version='1.0'><search /></jdisc></services>")
                                                  .build());
        assertThat(model.getHostSystem().getHosts().size(), is(1));
        assertThat(model.getContainerClusters().size(), is(1));
    }

    @Test
    public void testPermanentServices() throws IOException, SAXException {
        ApplicationPackage app = MockApplicationPackage.createEmpty();
        DeployState.Builder builder = new DeployState.Builder().applicationPackage(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), builder.build());
        assertThat(model.getContainerClusters().size(), is(0));
        model = new VespaModel(new NullConfigModelRegistry(), builder.permanentApplicationPackage(Optional.of(FilesApplicationPackage.fromFile(new File(TESTDIR, "app_permanent")))).build());
        assertThat(model.getContainerClusters().size(), is(1));
    }

}
