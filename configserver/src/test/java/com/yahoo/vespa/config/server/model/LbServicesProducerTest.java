// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.26
 */
public class LbServicesProducerTest {
    private static final String rotation1 = "rotation-1";
    private static final String rotation2 = "rotation-2";
    private static final String rotationString = rotation1 + "," + rotation2;
    private static final Set<Rotation> rotations = Collections.singleton(new Rotation(rotationString));

    @Test
    public void testDeterministicGetConfig() throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel = createTestModel(new DeployState.Builder().rotations(rotations));
        LbServicesConfig last = null;
        for (int i = 0; i < 100; i++) {
            testModel = randomizeTenant(testModel, i);
            LbServicesConfig config = getLbServicesConfig(Zone.defaultZone(), testModel);
            if (last != null) {
                assertConfig(last, config);
            }
            last = config;
        }
    }

    @Test
    public void testConfigAliases() throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel = createTestModel(new DeployState.Builder());
        LbServicesConfig conf = getLbServicesConfig(Zone.defaultZone(), testModel);
        final LbServicesConfig.Tenants.Applications.Hosts.Services services = conf.tenants("foo").applications("foo:prod:default:default").hosts("foo.foo.yahoo.com").services("qrserver");
        assertThat(services.servicealiases().size(), is(1));
        assertThat(services.endpointaliases().size(), is(2));

        assertThat(services.servicealiases(0), is("service1"));
        assertThat(services.endpointaliases(0), is("foo1.bar1.com"));
        assertThat(services.endpointaliases(1), is("foo2.bar2.com"));
    }

    @Test
    public void testConfigActiveRotation() throws IOException, SAXException {
        {
            RegionName regionName = RegionName.from("us-east-1");
            LbServicesConfig conf = createModelAndGetLbServicesConfig(regionName);
            assertTrue(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").activeRotation());
        }

        {
            RegionName regionName = RegionName.from("us-east-2");
            LbServicesConfig conf = createModelAndGetLbServicesConfig(regionName);
            assertFalse(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").activeRotation());
        }
    }

    private LbServicesConfig createModelAndGetLbServicesConfig(RegionName regionName) throws IOException, SAXException {
        final Zone zone = new Zone(Environment.prod, regionName);
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel = createTestModel(new DeployState.Builder()
                                                                                                 .zone(zone)
                                                                                                 .properties(new DeployProperties.Builder().build())
                                                                                                 .zone(zone));
        return getLbServicesConfig(new Zone(Environment.prod, regionName), testModel);
    }

    private LbServicesConfig getLbServicesConfig(Zone zone, Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel) {
        LbServicesProducer producer = new LbServicesProducer(testModel, zone);
        LbServicesConfig.Builder builder = new LbServicesConfig.Builder();
        producer.getConfig(builder);
        return new LbServicesConfig(builder);
    }

    @Test
    public void testConfigAliasesWithRotations() throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel = createTestModel(new DeployState.Builder().rotations(rotations));
        RegionName regionName = RegionName.from("us-east-1");
        LbServicesConfig conf = getLbServicesConfig(new Zone(Environment.prod, regionName), testModel);
        final LbServicesConfig.Tenants.Applications.Hosts.Services services = conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").hosts("foo.foo.yahoo.com").services("qrserver");
        assertThat(services.servicealiases().size(), is(1));
        assertThat(services.endpointaliases().size(), is(4));

        assertThat(services.servicealiases(0), is("service1"));
        assertThat(services.endpointaliases(0), is("foo1.bar1.com"));
        assertThat(services.endpointaliases(1), is("foo2.bar2.com"));
        assertThat(services.endpointaliases(2), is(rotation1));
        assertThat(services.endpointaliases(3), is(rotation2));
    }

    private Map<TenantName, Map<ApplicationId, ApplicationInfo>> randomizeTenant(Map<TenantName, Map<ApplicationId, ApplicationInfo>> testModel, int seed) {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> randomizedTenants = new LinkedHashMap<>();
        List<TenantName> keys = new ArrayList<>(testModel.keySet());
        Collections.shuffle(keys, new Random(seed));
        for (TenantName key : keys) {
            randomizedTenants.put(key, randomizeApplications(testModel.get(key), randomizedTenants.size()));
        }
        return randomizedTenants;
    }

    private Map<ApplicationId, ApplicationInfo> randomizeApplications(Map<ApplicationId, ApplicationInfo> applicationIdApplicationMap, int seed) {
        Map<ApplicationId, ApplicationInfo> randomizedApplications = new LinkedHashMap<>();
        List<ApplicationId> keys = new ArrayList<>(applicationIdApplicationMap.keySet());
        Collections.shuffle(keys, new Random(seed));
        for (ApplicationId key : keys) {
            randomizedApplications.put(key, applicationIdApplicationMap.get(key));
        }
        return randomizedApplications;
    }

    private Map<TenantName, Map<ApplicationId, ApplicationInfo>> createTestModel(DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        Map<TenantName, Map<ApplicationId, ApplicationInfo>> tMap = new LinkedHashMap<>();
        TenantName foo = TenantName.from("foo");
        TenantName bar = TenantName.from("bar");
        TenantName baz = TenantName.from("baz");
        tMap.put(foo, createTestApplications(foo, deployStateBuilder));
        tMap.put(bar, createTestApplications(bar, deployStateBuilder));
        tMap.put(baz, createTestApplications(baz, deployStateBuilder));
        return tMap;
    }

    private Map<ApplicationId, ApplicationInfo> createTestApplications(TenantName tenant, DeployState.Builder deploystateBuilder) throws IOException, SAXException {
        Map<ApplicationId, ApplicationInfo> aMap = new LinkedHashMap<>();
        ApplicationId fooApp = new ApplicationId.Builder().tenant(tenant).applicationName("foo").build();
        ApplicationId barApp = new ApplicationId.Builder().tenant(tenant).applicationName("bar").build();
        ApplicationId bazApp = new ApplicationId.Builder().tenant(tenant).applicationName("baz").build();
        aMap.put(fooApp, createApplication(fooApp, deploystateBuilder));
        aMap.put(barApp, createApplication(barApp, deploystateBuilder));
        aMap.put(bazApp, createApplication(bazApp, deploystateBuilder));
        return aMap;
    }

    private ApplicationInfo createApplication(ApplicationId appId, DeployState.Builder deploystateBuilder) throws IOException, SAXException {
        return new ApplicationInfo(
                appId,
                3l,
                createVespaModel(createApplicationPackage(
                        appId.tenant() + "." + appId.application() + ".yahoo.com", appId.tenant().value() + "." + appId.application().value() + "2.yahoo.com"),
                deploystateBuilder));
    }

    private ApplicationPackage createApplicationPackage(String host1, String host2) {
        String hosts = "<hosts><host name='" + host1 + "'><alias>node1</alias></host><host name='" + host2 + "'><alias>node2</alias></host></hosts>";
        String services = "<services><admin version='2.0'><adminserver hostalias='node1' /><logserver hostalias='node1' /><slobroks><slobrok hostalias='node1' /><slobrok hostalias='node2' /></slobroks></admin>"
                + "<jdisc id='mydisc' version='1.0'>" +
                "  <aliases>" +
                "      <endpoint-alias>foo2.bar2.com</endpoint-alias>" +
                "      <service-alias>service1</service-alias>" +
                "      <endpoint-alias>foo1.bar1.com</endpoint-alias>" +
                "  </aliases>" +
                "  <nodes>" +
                "    <node hostalias='node1' />" +
                "  </nodes>" +
                "  <search/>" +
                "</jdisc>" +
                "</services>";
        String deploymentInfo ="<?xml version='1.0' encoding='UTF-8'?>" +
                "<deployment version='1.0'>" +
                "  <test />" +
                "  <prod global-service-id='mydisc'>" +
                "    <region active='true'>us-east-1</region>" +
                "    <region active='false'>us-east-2</region>" +
                "  </prod>" +
                "</deployment>";

        return new MockApplicationPackage.Builder().withHosts(hosts).withServices(services).withDeploymentSpec(deploymentInfo).build();
    }

    private Model createVespaModel(ApplicationPackage applicationPackage, DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        return new VespaModel(new NullConfigModelRegistry(), deployStateBuilder.applicationPackage(applicationPackage).build(true));
    }

    private void assertConfig(LbServicesConfig expected, LbServicesConfig actual) {
        assertFalse(expected.toString().isEmpty());
        assertFalse(actual.toString().isEmpty());
        assertThat(expected.toString(), is(actual.toString()));
        assertThat(ConfigPayload.fromInstance(expected).toString(true), is(ConfigPayload.fromInstance(actual).toString(true)));
    }
}
