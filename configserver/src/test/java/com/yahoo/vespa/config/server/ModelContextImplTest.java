// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ModelContextImplTest {

    @Test
    public void testModelContextTest() {

        ContainerEndpoint endpoint = new ContainerEndpoint("foo", ApplicationClusterEndpoint.Scope.global, List.of("a", "b"));
        Set<ContainerEndpoint> endpoints = Collections.singleton(endpoint);
        InMemoryFlagSource flagSource = new InMemoryFlagSource();

        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .multitenant(true)
                .hostedVespa(false)
                .build();

        ApplicationPackage applicationPackage = MockApplicationPackage.createEmpty();
        HostProvisioner hostProvisioner = DeployState.getDefaultModelHostProvisioner(applicationPackage);
        ModelContext context = new ModelContextImpl(
                applicationPackage,
                Optional.empty(),
                new BaseDeployLogger(),
                new StaticConfigDefinitionRepo(),
                new MockFileRegistry(),
                new InThreadExecutorService(),
                Optional.empty(),
                hostProvisioner,
                new Provisioned(),
                new ModelContextImpl.Properties(
                        ApplicationId.defaultId(),
                        Version.emptyVersion,
                        configserverConfig,
                        Zone.defaultZone(),
                        endpoints,
                        false,
                        false,
                        flagSource,
                        null,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        List.of()),
                Optional.empty(),
                OnnxModelCost.disabled(),
                Optional.empty(),
                new Version(7),
                new Version(8));
        assertTrue(context.applicationPackage() instanceof MockApplicationPackage);
        assertEquals(hostProvisioner, context.getHostProvisioner());
        assertFalse(context.previousModel().isPresent());
        assertTrue(context.getFileRegistry() instanceof MockFileRegistry);
        assertTrue(context.configDefinitionRepo() instanceof StaticConfigDefinitionRepo);
        assertEquals(ApplicationId.defaultId(), context.properties().applicationId());
        assertTrue(context.properties().configServerSpecs().isEmpty());
        assertTrue(context.properties().multitenant());
        assertNotNull(context.properties().zone());
        assertFalse(context.properties().hostedVespa());
        assertEquals(endpoints, context.properties().endpoints());
        assertFalse(context.properties().isFirstTimeDeployment());

        assertEquals(Optional.empty(), context.wantedDockerImageRepo());
        assertEquals(new Version(7), context.modelVespaVersion());
        assertEquals(new Version(8), context.wantedNodeVespaVersion());
        assertTrue(context.properties().featureFlags().useAsyncMessageHandlingOnSchedule());
        assertEquals(0.5, context.properties().featureFlags().feedConcurrency(), 0.0);
    }

}
