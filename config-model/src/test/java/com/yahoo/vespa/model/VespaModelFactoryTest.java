// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.MockModelContext;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.*;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.answers.ThrowsExceptionClass;

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
import static org.mockito.Mockito.mock;

/**
 * @author lulf
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
    public void hostedVespaRoutingApplicationAllocatesNodesWithHostsXml() {
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
                        "        <nodes>\n" +
                        "            <node hostalias='proxy1' />\n" +
                        "        </nodes>\n" +
                        "    </jdisc>\n" +
                        "</services>";

        HostProvisioner provisionerToOverride =
                mock(HostProvisioner.class, new ThrowsExceptionClass(UnsupportedOperationException.class));

        ModelContext modelContext = new MockModelContext() {
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
                        return ApplicationId.HOSTED_ZONE_APPLICATION_ID;
                    }

                    @Override
                    public List<ConfigServerSpec> configServerSpecs() {
                        return Collections.emptyList();
                    }
                };
            }
        };

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

    @Test
    public void hostedVespaZoneApplicationAllocatesNodesWithHostsXml() {
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
                        "        <nodes>\n" +
                        "            <node hostalias='proxy1' />\n" +
                        "        </nodes>\n" +
                        "    </jdisc>\n" +
                        "</services>";

        HostProvisioner provisionerToOverride =
                mock(HostProvisioner.class, new ThrowsExceptionClass(UnsupportedOperationException.class));

        ModelContext modelContext = new MockModelContext() {
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
                        return ApplicationId.HOSTED_ZONE_APPLICATION_ID;
                    }

                    @Override
                    public List<ConfigServerSpec> configServerSpecs() {
                        return Collections.emptyList();
                    }
                };
            }
        };

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

    ApplicationPackage createApplicationPackageThatFailsWhenValidating() {
        return new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().failOnValidateXml().build();
    }

}
