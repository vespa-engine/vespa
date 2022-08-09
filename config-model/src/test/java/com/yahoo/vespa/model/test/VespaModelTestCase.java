// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.HostsXmlProvisioner;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.UnknownConfigIdException;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.application.validation.Validation;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

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

    private static VespaModel getVespaModel(String configPath) {
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(configPath);
        return creator.create(true);
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
    void testCommonConfig() {
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
    void testHostsConfig() {
        VespaModel model = getVespaModel(TESTDIR + "app_nohosts");
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
    void testHostsOverrides() {
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

    @Disabled
    @Test
    void testIllegalConfigIdWithBuilders() {
        assertThrows(UnknownConfigIdException.class, () -> {
            VespaModel model = getVespaModel(TESTDIR + "app_nohosts/");
            DocumentmanagerConfig.Builder db = new DocumentmanagerConfig.Builder();
            model.getConfig(db, "bogus");
        });
    }

    @Test
    void testConfigLists() {
        VespaModel model = getVespaModel(TESTDIR + "app_nohosts/");
        assertTrue(model.allConfigsProduced().size() > 0);
        assertTrue(model.allConfigIds().size() > 0);
    }

    @Test
    void testCreateFromReaders() {
        VespaModel model = new VespaModelCreatorWithMockPkg(
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
                        "</services>",
                ApplicationPackageUtils.generateSchemas("music"))
                .create();
        MessagebusConfig.Builder mBusB = new MessagebusConfig.Builder();
        model.getConfig(mBusB, "client");
        MessagebusConfig mBus = new MessagebusConfig(mBusB);
        assertEquals(mBus.routingtable().size(), 1);
    }

    @Test
    void testHostsWithoutAliases() {
        assertThrows(IllegalArgumentException.class, () -> {
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
        });
    }

    static class MyLogger implements DeployLogger {
        List<Pair<Level, String>> msgs = new ArrayList<>();
        @Override
        public void log(Level level, String message) {
            msgs.add(new Pair<>(level, message));
        }
    }

    @Test
    void testDeployLogger() throws IOException, SAXException {
        final String services = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                "<services  version=\"1.0\">" +
                "<config name=\"bar.unknsownfoo\">" +
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
        new Validation().validate(model, new ValidationParameters(ValidationParameters.IgnoreValidationErrors.TRUE), deployState);
        assertFalse(logger.msgs.isEmpty());
    }

    @Test
    void testNoAdmin() {
        VespaModel model = new VespaModelCreatorWithMockPkg(
                simpleHosts,
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                        "<services version=\"1.0\">" +
                        "</services>")
                .create();
        Admin admin = model.getAdmin();
        assertEquals(1, admin.getSlobroks().size());
        assertEquals(1, admin.getConfigservers().size());
        Set<HostInfo> hosts = model.getHosts();
        assertEquals(1, hosts.size());
        //logd, config proxy, sentinel, config server, slobrok, log server
        HostInfo host = hosts.iterator().next();
        assertEquals(7, host.getServices().size());
        new LogdConfig((LogdConfig.Builder) model.getConfig(new LogdConfig.Builder(), "admin/model"));

    }

    @Test
    void testNoMultitenantHostExported() throws IOException, SAXException {
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices("<services version='1.0'><admin version='3.0'><nodes count='1' /></admin></services>")
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .modelHostProvisioner(new InMemoryProvisioner(true, false, "host1.yahoo.com"))
                .properties(new TestProperties()
                        .setConfigServerSpecs(List.of(new TestProperties.Spec("cfghost", 1234, 1236)))
                        .setMultitenant(true))
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        AllocatedHosts info = model.allocatedHosts();
        assertEquals(0, info.getHosts().size(), "Admin version 3 is ignored, and there are no other hosts to borrow for admin services");
    }

    @Test
    void testMinimalApp() throws IOException, SAXException {
        VespaModel model = new VespaModel(new MockApplicationPackage.Builder()
                .withServices("<services version='1.0'><container version='1.0'><search /></container></services>")
                .build());
        assertEquals(1, model.hostSystem().getHosts().size());
        assertEquals(1, model.getContainerClusters().size());
    }

    @Test
    void testPermanentServices() throws IOException, SAXException {
        ApplicationPackage app = MockApplicationPackage.createEmpty();
        DeployState.Builder builder = new DeployState.Builder().applicationPackage(app);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), builder.build());
        assertTrue(model.getContainerClusters().isEmpty());
        model = new VespaModel(new NullConfigModelRegistry(), builder.permanentApplicationPackage(Optional.of(FilesApplicationPackage.fromFile(new File(TESTDIR, "app_permanent")))).build());
        assertEquals(1, model.getContainerClusters().size());
    }

    @Test
    void testThatDeployLogContainsWarningWhenUsingSearchdefinitionsDir() throws IOException, SAXException {
        ApplicationPackage app = FilesApplicationPackage.fromFile(
                new File("src/test/cfg/application/deprecated_features_app/"));
        MyLogger logger = new MyLogger();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .deployLogger(logger)
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new Validation().validate(model, new ValidationParameters(), deployState);
        assertContainsWarning(logger.msgs, "Directory searchdefinitions/ should not be used for schemas, use schemas/ instead");
    }

    private void assertContainsWarning(List<Pair<Level,String>> msgs, String text) {
        boolean foundCorrectWarning = false;
        for (var msg : msgs)
            if (msg.getFirst().getName().equals("WARNING") && msg.getSecond().equals(text)) {
                foundCorrectWarning = true;
            }
        if (! foundCorrectWarning) for (var msg : msgs) System.err.println("MSG: "+msg);
        assertTrue(msgs.size() > 0);
        assertTrue(foundCorrectWarning);
    }

}
