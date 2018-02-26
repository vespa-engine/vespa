// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.MockModelContext;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class VespaModelFactoryTest {

    private ModelContext testModelContext;

    @Before
    public void setupContext() {
        testModelContext = new MockModelContext();
    }

    @Test
    public void testThatFactoryCanBuildModel() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        Model model = modelFactory.createModel(testModelContext);
        assertNotNull(model);
        assertTrue(model instanceof VespaModel);
    }

    // Uses an application package that throws IllegalArgumentException when validating
    @Test(expected = IllegalArgumentException.class)
    public void testThatFactoryModelValidationFailsWithIllegalArgumentException() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        modelFactory.createAndValidateModel(new MockModelContext(createApplicationPackageThatFailsWhenValidating()), false);
    }

    // Uses a MockApplicationPackage that throws throws UnsupportedOperationException (rethrown as RuntimeException) when validating
    @Test(expected = RuntimeException.class)
    public void testThatFactoryModelValidationFails() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        modelFactory.createAndValidateModel(testModelContext, false);
    }

    @Test
    public void testThatFactoryModelValidationCanBeIgnored() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        ModelCreateResult createResult = modelFactory.createAndValidateModel(
                new MockModelContext(createApplicationPackageThatFailsWhenValidating()),
                true);
        assertNotNull(createResult.getModel());
        assertNotNull(createResult.getConfigChangeActions());
        assertTrue(createResult.getConfigChangeActions().isEmpty());
    }

    @Test
    public void hostedVespaZoneApplicationAllocatesNodesFromNodeRepo() {
        String hostName = "test-host-name";
        String routingClusterName = "routing-cluster";

        String hosts =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<hosts>\n" +
                        "  <host name='" + hostName + "'>\n" +
                        "    <alias>proxy1</alias>\n" +
                        "  </host>\n" +
                        "</hosts>";

        String services =
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                        "<services version='1.0' xmlns:deploy='vespa'>\n" +
                        "    <admin version='2.0'>\n" +
                        "        <adminserver hostalias='proxy1' />\n" +
                        "    </admin>" +
                        "    <jdisc id='" + routingClusterName + "' version='1.0'>\n" +
                        "        <nodes type='proxy'/>\n" +
                        "    </jdisc>\n" +
                        "</services>";

        HostProvisioner provisionerToOverride = new HostProvisioner() {
            @Override
            public HostSpec allocateHost(String alias) {
                return new HostSpec(hostName,
                                    Collections.emptyList(),
                                    ClusterMembership.from(ClusterSpec.from(ClusterSpec.Type.admin,
                                                                            new ClusterSpec.Id(routingClusterName),
                                                                            ClusterSpec.Group.from(0),
                                                                            Version.fromString("6.42")),
                                                           0));
            }

            @Override
            public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) {
                return Collections.singletonList(new HostSpec(hostName,
                                                              Collections.emptyList(),
                                                              ClusterMembership.from(ClusterSpec.from(ClusterSpec.Type.container,
                                                                                                      new ClusterSpec.Id(routingClusterName),
                                                                                                      ClusterSpec.Group.from(0),
                                                                                                      Version.fromString("6.42")),
                                                                                     0)));
            }
        };

        ModelContext modelContext = createMockModelContext(hosts, services, provisionerToOverride);
        Model model = new VespaModelFactory(new NullConfigModelRegistry()).createModel(modelContext);

        List<HostInfo> allocatedHosts = new ArrayList<>(model.getHosts());
        assertThat(allocatedHosts.size(), is(1));
        HostInfo hostInfo = allocatedHosts.get(0);

        assertThat(hostInfo.getHostname(), is(hostName));
        assertTrue("Routing service should run on host " + hostName,
                   hostInfo.getServices().stream()
                           .map(ServiceInfo::getConfigId)
                           .anyMatch(configId -> configId.contains(routingClusterName)));
    }

    private ModelContext createMockModelContext(String hosts, String services, HostProvisioner provisionerToOverride) {
        return new MockModelContext() {
            @Override
            public ApplicationPackage applicationPackage() {
                return new MockApplicationPackage.Builder().withHosts(hosts).withServices(services).build();
            }

            @Override
            public Optional<HostProvisioner> hostProvisioner() {
                return Optional.of(provisionerToOverride);
            }

            @Override
            public Properties properties() {
                return new Properties() {
                    @Override
                    public boolean multitenant() {
                        return true;
                    }

                    @Override
                    public boolean hostedVespa() {
                        return true;
                    }

                    @Override
                    public Zone zone() {
                        return Zone.defaultZone();
                    }

                    @Override
                    public Set<Rotation> rotations() {
                        return new HashSet<>();
                    }

                    @Override
                    public ApplicationId applicationId() {
                        return ApplicationId.from(TenantName.from("hosted-vespa"),
                                                  ApplicationName.from("routing"),
                                                  InstanceName.defaultName());
                    }

                    @Override
                    public List<ConfigServerSpec> configServerSpecs() {
                        return Collections.emptyList();
                    }

                    @Override
                    public HostName loadBalancerName() {
                        return null;
                    }
                };
            }
        };
    }

    ApplicationPackage createApplicationPackageThatFailsWhenValidating() {
        return new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().failOnValidateXml().build();
    }

}
