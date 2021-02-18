// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.yahoo.config.model.api.container.ContainerServiceType.QRSERVER;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * @author Ulf Lilleengen
 */
@RunWith(Parameterized.class)
public class LbServicesProducerTest {

    private static final String rotation1 = "rotation-1";
    private static final String rotation2 = "rotation-2";
    private static final Set<ContainerEndpoint> endpoints = Set.of(
            new ContainerEndpoint("mydisc", List.of("rotation-1", "rotation-2"))
    );
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final boolean useGlobalServiceId;

    @Parameterized.Parameters
    public static Object[] useGlobalServiceId() {
        return new Object[] { true, false };
    }

    public LbServicesProducerTest(boolean useGlobalServiceId) {
        this.useGlobalServiceId = useGlobalServiceId;
    }

    @Test
    public void testDeterministicGetConfig() {
        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder().endpoints(endpoints));
        LbServicesConfig last = null;
        for (int i = 0; i < 100; i++) {
            testModel = randomizeApplications(testModel, i);
            LbServicesConfig config = getLbServicesConfig(Zone.defaultZone(), testModel);
            if (last != null) {
                assertConfig(last, config);
            }
            last = config;
        }
    }

    @Test
    public void testConfigAliases() {
        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder());
        LbServicesConfig conf = getLbServicesConfig(Zone.defaultZone(), testModel);
        LbServicesConfig.Tenants.Applications.Hosts.Services services =
                conf.tenants("foo").applications("foo:prod:default:default").hosts("foo.foo.yahoo.com").services(QRSERVER.serviceName);
        assertThat(services.servicealiases().size(), is(1));
        assertThat(services.endpointaliases().size(), is(2));

        assertThat(services.servicealiases(0), is("service1"));
        assertThat(services.endpointaliases(0), is("foo1.bar1.com"));
        assertThat(services.endpointaliases(1), is("foo2.bar2.com"));
    }

    @Test
    public void testConfigActiveRotation() {
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

    @Test
    public void use_power_of_two_lb_is_configured_from_feature_flag() {
            RegionName regionName = RegionName.from("us-east-1");

            LbServicesConfig conf = createModelAndGetLbServicesConfig(regionName);
            assertFalse(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").usePowerOfTwoChoicesLb());

            flagSource.withBooleanFlag(Flags.USE_POWER_OF_TWO_CHOICES_LOAD_BALANCING.id(), true);
            conf = createModelAndGetLbServicesConfig(regionName);
            assertTrue(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").usePowerOfTwoChoicesLb());
    }

    @Test
    public void generate_non_mtls_endpoints_from_feature_flag() {
        RegionName regionName = RegionName.from("us-east-1");

        LbServicesConfig conf = createModelAndGetLbServicesConfig(regionName);
        assertTrue(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").generateNonMtlsEndpoint());

        flagSource.withBooleanFlag(Flags.GENERATE_NON_MTLS_ENDPOINT.id(), false);
        conf = createModelAndGetLbServicesConfig(regionName);
        assertFalse(conf.tenants("foo").applications("foo:prod:" + regionName.value() + ":default").generateNonMtlsEndpoint());
    }

    private LbServicesConfig createModelAndGetLbServicesConfig(RegionName regionName) {
        Zone zone = new Zone(Environment.prod, regionName);
        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder().zone(zone));
        return getLbServicesConfig(new Zone(Environment.prod, regionName), testModel);
    }

    private LbServicesConfig getLbServicesConfig(Zone zone, Map<TenantName, Set<ApplicationInfo>> testModel) {
        LbServicesProducer producer = new LbServicesProducer(testModel, zone, flagSource);
        LbServicesConfig.Builder builder = new LbServicesConfig.Builder();
        producer.getConfig(builder);
        return new LbServicesConfig(builder);
    }

    @Test
    public void testConfigAliasesWithEndpoints() {
        assumeFalse(useGlobalServiceId);

        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder()
                .endpoints(endpoints)
                .properties(new TestProperties().setHostedVespa(true)));
        RegionName regionName = RegionName.from("us-east-1");

        var services = getLbServicesConfig(new Zone(Environment.prod, regionName), testModel)
                .tenants("foo")
                .applications("foo:prod:" + regionName.value() + ":default")
                .hosts("foo.foo.yahoo.com")
                .services(QRSERVER.serviceName);

        assertThat(services.servicealiases(), contains("service1"));
        assertThat("Missing endpoints in list: " + services.endpointaliases(), services.endpointaliases(), containsInAnyOrder("foo1.bar1.com", "foo2.bar2.com", rotation1, rotation2));
    }


    @Test
    public void testRoutingConfigForTesterApplication() {
        assumeFalse(useGlobalServiceId);

        Map<TenantName, Set<ApplicationInfo>> testModel = createTestModel(new DeployState.Builder());
        LbServicesConfig conf = getLbServicesConfig(Zone.defaultZone(), testModel);
        LbServicesConfig.Tenants.Applications.Hosts.Services services = conf.tenants("foo").applications("foo:prod:default:default").hosts("foo.foo.yahoo.com").services(QRSERVER.serviceName);
        assertThat(services.servicealiases().size(), is(1));
        assertThat(services.endpointaliases().size(), is(2));

        // No config for tester application
        assertNull(getLbServicesConfig(Zone.defaultZone(), testModel)
                           .tenants("foo")
                           .applications("baz:prod:default:custom-t"));
    }

    private Map<TenantName, Set<ApplicationInfo>> randomizeApplications(Map<TenantName, Set<ApplicationInfo>> testModel, int seed) {
        Map<TenantName, Set<ApplicationInfo>> randomizedApplications = new LinkedHashMap<>();
        List<TenantName> keys = new ArrayList<>(testModel.keySet());
        Collections.shuffle(keys, new Random(seed));
        for (TenantName key : keys) {
            randomizedApplications.put(key, testModel.get(key));
        }
        return randomizedApplications;
    }

    private Map<TenantName, Set<ApplicationInfo>> createTestModel(DeployState.Builder deployStateBuilder) {
        deployStateBuilder.properties(new TestProperties().setHostedVespa(true));

        Map<TenantName, Set<ApplicationInfo>> tMap = new LinkedHashMap<>();
        TenantName foo = TenantName.from("foo");
        TenantName bar = TenantName.from("bar");
        TenantName baz = TenantName.from("baz");
        tMap.put(foo, createTestApplications(foo, deployStateBuilder));
        tMap.put(bar, createTestApplications(bar, deployStateBuilder));
        tMap.put(bar, createTestApplications(baz, deployStateBuilder));
        return tMap;
    }

    private Set<ApplicationInfo> createTestApplications(TenantName tenant, DeployState.Builder deployStateBuilder) {
        ApplicationId fooApp = new ApplicationId.Builder().tenant(tenant).applicationName("foo").build();
        ApplicationId barApp = new ApplicationId.Builder().tenant(tenant).applicationName("bar").build();
        ApplicationId bazApp = new ApplicationId.Builder().tenant(tenant).applicationName("baz").instanceName("custom-t").build(); // tester app
        return new LinkedHashSet<>(createApplication(List.of(fooApp, barApp, bazApp), deployStateBuilder));
    }

    private Set<ApplicationInfo> createApplication(List<ApplicationId> appIds, DeployState.Builder deployStateBuilder) {
        Set<ApplicationInfo> applicationInfoSet = new HashSet<>();
        List<String> hostnames = new ArrayList<>();
        appIds.forEach(appId -> {
            hostnames.add(appId.tenant() + "." + appId.application() + ".yahoo.com");
            hostnames.add(appId.tenant().value() + "." + appId.application().value() + "2.yahoo.com");
            try {
                InMemoryProvisioner provisioner = new InMemoryProvisioner(true, false, hostnames);
                deployStateBuilder.modelHostProvisioner(provisioner);
                applicationInfoSet.add(new ApplicationInfo(appId, 3, createVespaModel(createApplicationPackage(), deployStateBuilder)));
            } catch (IOException | SAXException e) {
                throw new RuntimeException(e);
            }
        });

        return applicationInfoSet;
    }

    private ApplicationPackage createApplicationPackage() {
        String services = "<services>" +
                          "<admin version='4.0'><logservers> <nodes count='1' /> </logservers></admin>" +
                          "  <container id='mydisc' version='1.0'>" +
                          "    <aliases>" +
                          "      <endpoint-alias>foo2.bar2.com</endpoint-alias>" +
                          "      <service-alias>service1</service-alias>" +
                          "      <endpoint-alias>foo1.bar1.com</endpoint-alias>" +
                          "    </aliases>" +
                          "    <nodes count='1' />" +
                          "    <search/>" +
                          "  </container>" +
                          "</services>";

        String deploymentInfo;

        if (useGlobalServiceId) {
            deploymentInfo ="<?xml version='1.0' encoding='UTF-8'?>" +
                    "<deployment version='1.0'>" +
                    "  <test />" +
                    "  <prod global-service-id='mydisc'>" +
                    "    <region active='true'>us-east-1</region>" +
                    "    <region active='false'>us-east-2</region>" +
                    "  </prod>" +
                    "</deployment>";
        } else {
            deploymentInfo ="<?xml version='1.0' encoding='UTF-8'?>" +
                    "<deployment version='1.0'>" +
                    "  <test />" +
                    "  <prod>" +
                    "    <region active='true'>us-east-1</region>" +
                    "    <region active='false'>us-east-2</region>" +
                    "  </prod>" +
                    "  <endpoints>" +
                    "    <endpoint container-id='mydisc' />" +
                    "  </endpoints>" +
                    "</deployment>";
        }


        return new MockApplicationPackage.Builder().withServices(services).withDeploymentSpec(deploymentInfo).build();
    }

    private Model createVespaModel(ApplicationPackage applicationPackage, DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        return new VespaModel(new NullConfigModelRegistry(), deployStateBuilder.applicationPackage(applicationPackage).build());
    }

    private void assertConfig(LbServicesConfig expected, LbServicesConfig actual) {
        assertFalse(expected.toString().isEmpty());
        assertFalse(actual.toString().isEmpty());
        assertThat(expected.toString(), is(actual.toString()));
        assertThat(ConfigPayload.fromInstance(expected).toString(true), is(ConfigPayload.fromInstance(actual).toString(true)));
    }

}
