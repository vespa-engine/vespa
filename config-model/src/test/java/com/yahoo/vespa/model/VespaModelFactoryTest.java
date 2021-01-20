// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.MockModelContext;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        modelFactory.createAndValidateModel(new MockModelContext(createApplicationPackageThatFailsWhenValidating()), new ValidationParameters());
    }

    // Uses a MockApplicationPackage that throws throws UnsupportedOperationException (rethrown as RuntimeException) when validating
    @Test(expected = RuntimeException.class)
    public void testThatFactoryModelValidationFails() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        modelFactory.createAndValidateModel(testModelContext, new ValidationParameters());
    }

    @Test
    public void testThatFactoryModelValidationCanBeIgnored() {
        VespaModelFactory modelFactory = new VespaModelFactory(new NullConfigModelRegistry());
        ModelCreateResult createResult = modelFactory.createAndValidateModel(
                new MockModelContext(createApplicationPackageThatFailsWhenValidating()),
                new ValidationParameters(ValidationParameters.IgnoreValidationErrors.TRUE));
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
                        "    <container id='" + routingClusterName + "' version='1.0'>\n" +
                        "        <nodes type='proxy'/>\n" +
                        "    </container>\n" +
                        "</services>";

        HostProvisioner provisionerToOverride = new HostProvisioner() {
            @Override
            public HostSpec allocateHost(String alias) {
                return new HostSpec(hostName,
                                    NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
                                    ClusterMembership.from(ClusterSpec.request(ClusterSpec.Type.admin, new ClusterSpec.Id(routingClusterName)).vespaVersion("6.42").build(), 0),
                                    Optional.empty(), Optional.empty(), Optional.empty());
            }

            @Override
            public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
                return List.of(new HostSpec(hostName,
                                            NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
                                            ClusterMembership.from(ClusterSpec.request(ClusterSpec.Type.container, new ClusterSpec.Id(routingClusterName)).vespaVersion("6.42").build(), 0),
                                            Optional.empty(), Optional.empty(), Optional.empty()));
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
            public HostProvisioner getHostProvisioner() { return provisionerToOverride; }

            @Override
            public Properties properties() {
                return new TestProperties();
            }
        };
    }

    ApplicationPackage createApplicationPackageThatFailsWhenValidating() {
        return new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().failOnValidateXml().build();
    }

}
