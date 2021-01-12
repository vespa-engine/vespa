// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.cloud.config.LbServicesConfig.Tenants.Applications;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.model.SuperModelConfigProvider;
import com.yahoo.vespa.config.server.rpc.UncompressedConfigResponseFactory;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SuperModelControllerTest {

    private SuperModelController handler;

    @Before
    public void setupHandler() throws IOException, SAXException {
        Map<ApplicationId, ApplicationInfo> models = new LinkedHashMap<>();

        File testApp = new File("src/test/resources/deploy/app");
        ApplicationId app = ApplicationId.from(TenantName.from("a"),
                                               ApplicationName.from("foo"), InstanceName.defaultName());
        models.put(app, new ApplicationInfo(app, 4L, new VespaModel(FilesApplicationPackage.fromFile(testApp))));
        SuperModel superModel = new SuperModel(models, true);
        handler = new SuperModelController(new SuperModelConfigProvider(superModel, Zone.defaultZone(), new InMemoryFlagSource()), new TestConfigDefinitionRepo(), 2, new UncompressedConfigResponseFactory());
    }
    
    @Test
    public void test_lb_config_simple() {
        LbServicesConfig.Builder lb = new LbServicesConfig.Builder();
        handler.getSuperModel().getConfig(lb);
        LbServicesConfig lbc = new LbServicesConfig(lb);
        assertThat(lbc.tenants().size(), is(1));
        assertThat(lbc.tenants("a").applications().size(), is(1));
        Applications app = lbc.tenants("a").applications("foo:prod:default:default");
        assertTrue(app.hosts().size() > 0);
    }


    @Test(expected = UnknownConfigDefinitionException.class)
    public void test_unknown_config_definition() {
        String md5 = "asdfasf";
        Request request = JRTClientConfigRequestV3.createWithParams(new ConfigKey<>("foo", "id", "bar", md5, null), DefContent.fromList(Collections.emptyList()),
                                                                    "fromHost", md5, 1, 1, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                                                    Optional.empty())
                                                  .getRequest();
        JRTServerConfigRequestV3 v3Request = JRTServerConfigRequestV3.createFromRequest(request);
        handler.resolveConfig(v3Request);
    }

    @Test
    public void test_lb_config_multiple_apps() throws IOException, SAXException {
        Map<ApplicationId, ApplicationInfo> models = new LinkedHashMap<>();
        TenantName t1 = TenantName.from("t1");
        TenantName t2 = TenantName.from("t2");
        File testApp1 = new File("src/test/resources/deploy/app");
        File testApp2 = new File("src/test/resources/deploy/advancedapp");
        File testApp3 = new File("src/test/resources/deploy/advancedapp");

        ApplicationId simple = applicationId("mysimpleapp", t1);
        ApplicationId advanced = applicationId("myadvancedapp", t1);
        ApplicationId tooAdvanced = applicationId("minetooadvancedapp", t2);
        models.put(simple, createApplicationInfo(testApp1, simple, 4L));
        models.put(advanced, createApplicationInfo(testApp2, advanced, 4L));
        models.put(tooAdvanced, createApplicationInfo(testApp3, tooAdvanced, 4L));

        SuperModel superModel = new SuperModel(models, true);
        SuperModelController han = new SuperModelController(new SuperModelConfigProvider(superModel, Zone.defaultZone(), new InMemoryFlagSource()), new TestConfigDefinitionRepo(), 2, new UncompressedConfigResponseFactory());
        LbServicesConfig.Builder lb = new LbServicesConfig.Builder();
        han.getSuperModel().getConfig(lb);
        LbServicesConfig lbc = new LbServicesConfig(lb);
        assertThat(lbc.tenants().size(), is(2));
        assertThat(lbc.tenants("t1").applications().size(), is(2));
        assertThat(lbc.tenants("t2").applications().size(), is(1));
        assertThat(lbc.tenants("t2").applications("minetooadvancedapp:prod:default:default").hosts().size(), is(1));
        assertQrServer(lbc.tenants("t2").applications("minetooadvancedapp:prod:default:default"));
    }

    private ApplicationId applicationId(String applicationName, TenantName tenantName) {
        return ApplicationId.from(tenantName, ApplicationName.from(applicationName), InstanceName.defaultName());
    }

    private void assertQrServer(Applications app) {
        String host = app.hosts().keySet().iterator().next();
        Applications.Hosts hosts = app.hosts(host);
        assertThat(hosts.hostname(), is(host));
        for (Map.Entry<String, Applications.Hosts.Services> e : app.hosts(host).services().entrySet()) {
            if (QRSERVER.serviceName.equals(e.getKey())) {
                Applications.Hosts.Services s = e.getValue();
                assertThat(s.type(), is("qrserver"));
                assertThat(s.ports().size(), is(4));
                assertThat(s.ports().get(0).number(), is(8000));
                assertThat(s.index(), is(0));
                return;
            }
        }
        org.junit.Assert.fail("No container service in config");
    }

    private DeployState createDeployState(File applicationPackage, ApplicationId applicationId) {
        return new DeployState.Builder()
                .applicationPackage(FilesApplicationPackage.fromFile(applicationPackage))
                .properties(new TestProperties().setApplicationId(applicationId))
                .build();
    }

    private ApplicationInfo createApplicationInfo(File applicationPackage, ApplicationId applicationId, long generation) throws IOException, SAXException {
        DeployState deployState = createDeployState(applicationPackage, applicationId);
        return new ApplicationInfo(applicationId, generation, new VespaModel(deployState));
    }

}
