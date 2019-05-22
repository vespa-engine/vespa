// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Test;

import java.util.Collections;
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

        final Rotation rotation = new Rotation("this.is.a.mock.rotation");
        final Set<Rotation> rotations = Collections.singleton(rotation);
        final InMemoryFlagSource flagSource = new InMemoryFlagSource();

        ModelContext context = new ModelContextImpl(
                MockApplicationPackage.createEmpty(),
                Optional.empty(),
                Optional.empty(),
                new BaseDeployLogger(),
                new StaticConfigDefinitionRepo(),
                new MockFileRegistry(),
                Optional.empty(),
                new ModelContextImpl.Properties(
                        ApplicationId.defaultId(),
                        true,
                        Collections.emptyList(),
                        null,
                        null,
                        null,
                        false,
                        Zone.defaultZone(),
                        rotations,
                        false,
                        false,
                        flagSource),
                Optional.empty(),
                new Version(6), 
                new Version(6));
        assertTrue(context.applicationPackage() instanceof MockApplicationPackage);
        assertFalse(context.hostProvisioner().isPresent());
        assertFalse(context.permanentApplicationPackage().isPresent());
        assertFalse(context.previousModel().isPresent());
        assertTrue(context.getFileRegistry() instanceof MockFileRegistry);
        assertTrue(context.configDefinitionRepo() instanceof StaticConfigDefinitionRepo);
        assertEquals(ApplicationId.defaultId(), context.properties().applicationId());
        assertTrue(context.properties().configServerSpecs().isEmpty());
        assertTrue(context.properties().multitenant());
        assertNotNull(context.properties().zone());
        assertFalse(context.properties().hostedVespa());
        assertEquals(rotations, context.properties().rotations());
        assertFalse(context.properties().isFirstTimeDeployment());
        assertTrue(context.properties().useDedicatedNodeForLogserver());
    }
}
