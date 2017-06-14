// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.test.StandardConfig;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.net.HostName;
import com.yahoo.vespa.model.*;
import com.yahoo.vespa.model.test.ApiConfigModel;
import com.yahoo.vespa.model.test.SimpleConfigModel;
import com.yahoo.vespa.model.test.SimpleService;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class SystemModelTestCase {

    private static final String TESTDIR = "src/test/cfg/application/";

    private static VespaModel getVespaModelDoNotValidateXml(String configPath) {
        ConfigModelRegistry registry = MapConfigModelRegistry.createFromList(new SimpleConfigModel.Builder(), new ApiConfigModel.Builder());
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(configPath,registry);
        return creator.create(false);  // do not validate against schema  -- the xml files used here are not valid
    }

    // Debugging
    @SuppressWarnings({"UnusedDeclaration"})
    private void dumpTree(ConfigProducer producer) {
        Map<String,? extends ConfigProducer> id2cp = producer.getChildren();
        for (ConfigProducer c : id2cp.values()) {
            System.out.println("id: " + c.getConfigId());
            if (c.getChildren().size() > 0) {
                dumpTree(c);
            }
        }
    }

    @Test
    public void testMetrics() {
        VespaModel vespaModel = getVespaModelDoNotValidateXml(TESTDIR + "metricsconfig");
        SimpleService service0 = (SimpleService)vespaModel.getConfigProducer("simple/simpleservice.0").get();
        vespaModel.getConfigProducer("simple/simpleservice.1");
        assertThat(service0.getDefaultMetricDimensions().get("clustername"), is("testClusterName"));
    }

    @Test
    public void testVespaModel() {
        VespaModel vespaModel = getVespaModelDoNotValidateXml(TESTDIR + "simpleconfig/");
        assertNotNull(vespaModel);

        assertEquals("There are two instances of the simple model + Routing and AdminModel (set up implicitly)", 4, vespaModel.configModelRepo().asMap().size());
        assertNotNull("One gets the default name as there is no explicit id", vespaModel.configModelRepo().asMap().get("simple"));
        assertNotNull("The other gets the explicit id as name", vespaModel.configModelRepo().asMap().get("second"));

        ApplicationConfigProducerRoot root = vespaModel.getVespa();
        assertNotNull(root);

        // Verify configIds from vespa
        assertTrue(6 <= root.getConfigIds().size());
        assertTrue(root.getConfigIds().contains("client"));
        assertTrue(root.getConfigIds().contains("simple"));
        assertTrue(root.getConfigIds().contains("second"));
        assertTrue(root.getConfigIds().contains("simple/simpleservice.0"));
        assertTrue(root.getConfigIds().contains("simple/simpleservice.1"));
        assertTrue(root.getConfigIds().contains("second/simpleservice.0"));

        // Verify configIds from vespaModel
        assertTrue(12 <= vespaModel.getConfigIds().size());
        String localhost = HostName.getLocalhost();
        String localhostConfigId = "hosts/" + localhost;
        Set<String> configIds = vespaModel.getConfigIds();
        assertTrue(configIds.contains("client"));
        assertTrue(configIds.contains(localhostConfigId));
        assertTrue(configIds.contains("simple/simpleservice.0"));
        assertTrue(configIds.contains("second/simpleservice.0"));
        assertTrue(configIds.contains("hosts/" + localhost + "/logd"));

        // Verify sentinel config
        SentinelConfig sentinelConfig = new SentinelConfig((SentinelConfig.Builder) vespaModel.getConfig(new SentinelConfig.Builder(), localhostConfigId));
        boolean found = false;
        for (SentinelConfig.Service service : sentinelConfig.service()) {
            if ("logd".equals(service.name())) {
                found = true;
            }
        }
        assertTrue(found);

        // Get the simple model config from VespaModel
        assertEquals(vespaModel.getConfig(StandardConfig.class, "simple/simpleservice.0").astring(), "simpleservice");
        assertEquals(vespaModel.getConfig(StandardConfig.class, "second/simpleservice.0").astring(), "simpleservice");
    }

    @Test
    public void testHostSystem() {
        VespaModel vespaModel = getVespaModelDoNotValidateXml(TESTDIR + "simpleconfig/");
        HostSystem hostSystem = vespaModel.getHostSystem();

        HostResource host1 = hostSystem.getHost("host1");
        HostResource host2 = hostSystem.getHost("host2");
        HostResource host3 = hostSystem.getHost("host3");

        assertEquals(host1, host2);
        assertEquals(host2, host3);

        // all three host aliases are for the same host, so the number of services should be 3 + 8
        // (3 simpleservices and logd, configproxy, config sentinel, admin server config server, slobrok, log server and file distribution)
        assertEquals(10, host1.getServices().size());

        assertNotNull(host1.getService("simpleservice"));
        assertNotNull(host1.getService("simpleservice2"));
        assertNotNull(host3.getService("simpleservice3"));
    }

    @Test
    public void testBasePorts() {
        VespaModel vespaModel = getVespaModelDoNotValidateXml(TESTDIR + "simpleconfig");
        assertNotNull(vespaModel);

        assertEquals(vespaModel.getConfig(StandardConfig.class, "simple/simpleservice.0").baseport(), 10000);
        assertTrue(vespaModel.getConfig(StandardConfig.class, "simple/simpleservice.1").baseport() != 10000);
    }

    /**
     * This test is the same as the system test cloudconfig/plugins.
     * Be sure to update it as well if you change this.
     */
    @Test
    public void testPlugins() {
        VespaModel vespaModel = getVespaModelDoNotValidateXml(TESTDIR + "plugins");

        assertNotNull(vespaModel);
        ApplicationConfigProducerRoot root = vespaModel.getVespa();

        assertEquals(5, vespaModel.configModelRepo().asMap().size());
        assertTrue(vespaModel.configModelRepo().asMap().keySet().contains("simple"));
        assertTrue(vespaModel.configModelRepo().asMap().keySet().contains("api"));
        assertTrue(root.getConfigIds().contains("simple/simpleservice.0"));
        assertTrue(root.getConfigIds().contains("simple/simpleservice.1"));
        assertTrue(root.getConfigIds().contains("api/apiservice.0"));

        // Verify that configModelRegistry iterates in dependency order
        Iterator<ConfigModel> i = vespaModel.configModelRepo().iterator();
        ConfigModel plugin = i.next();
        assertEquals("admin", plugin.getId());
        plugin = i.next();
        assertEquals("simple", plugin.getId());
        plugin = i.next();
        assertEquals("simple2", plugin.getId());
        plugin = i.next();
        assertEquals("api", plugin.getId());
        plugin = i.next();
        assertEquals("routing", plugin.getId());

        assertEquals(vespaModel.getConfig(StandardConfig.class, "api/apiservice.0").astring(), "apiservice");
        
        assertEquals(vespaModel.getConfig(StandardConfig.class, "simple/simpleservice.0").astring(), "simpleservice");
        assertEquals(vespaModel.getConfig(StandardConfig.class, "simple/simpleservice.1").astring(), "simpleservice");
        assertEquals(vespaModel.getConfig(StandardConfig.class, "simple2/simpleservice.0").astring(), "simpleservice");
    }

    @Test
    public void testEqualPlugins() {
        try {
            getVespaModelDoNotValidateXml(TESTDIR + "doubleconfig");
            fail("No exception upon two plugins with the same name");
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage(), is("Could not resolve tag <simpleplugin version=\"1.0\"> to a config model component"));
        }
    }

}
