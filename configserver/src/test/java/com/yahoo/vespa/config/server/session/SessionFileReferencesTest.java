// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.NotFoundException;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SessionFileReferencesTest {
    private static final ApplicationId application = ApplicationId.from("default", "app", "default");
    private static final Version firstVersion = new Version(1, 2, 3);
    private static final Version secondVersion = new Version(3, 2, 1);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final MockCurator curator = new MockCurator();
    private final InMemoryFlagSource flags = new InMemoryFlagSource();
    private FileDistributionFactory factory;
    private TenantRepository tenants;
    private ApplicationRepository applications;
    private SessionRepository sessions;

    @Before
    public void setUp() throws IOException {
        ConfigserverConfig config = new ConfigserverConfig.Builder()
                .hostedVespa(true)
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        factory = new FileDistributionFactory(config, new FileDirectory(config));
        tenants = new TestTenantRepository.Builder()
                .withConfigserverConfig(config)
                .withCurator(curator)
                .withFlagSource(flags)
                .withFileDistributionFactory(factory)
                .withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(
                        new MockProvisioner().hostProvisioner(new InMemoryProvisioner(9, false)), config))
                .withModelFactoryRegistry(new ModelFactoryRegistry(List.of(modelFactory(firstVersion), modelFactory(secondVersion))))
                .build();
        tenants.addTenant(application.tenant());
        applications = new ApplicationRepository.Builder().withTenantRepository(tenants).withConfigserverConfig(config).withFlagSource(flags).build();
        sessions = tenants.getTenant(application.tenant()).getSessionRepository();
    }

    @After
    public void tearDown() {
        if (tenants != null) tenants.close();
        if (factory != null) factory.close();
    }

    @Test
    public void prepareUsesSha256AcrossModelsAndReloadKeepsSavedReferences() {
        long session = deploy();
        Map<Version, FileRegistry> registries = reload(session);
        assertEquals(2, registries.size());
        for (FileRegistry registry : registries.values()) {
            assertTrue(registry.export().stream().map(entry -> entry.relativePath).toList()
                    .containsAll(List.of("model-" + firstVersion, "model-" + secondVersion)));
            for (FileRegistry.Entry entry : registry.export()) assertEquals(64, entry.reference.value().length());
            assertEquals(64, registry.addBlob("model-" + firstVersion, ByteBuffer.allocate(0)).value().length());
        }
    }

    @Test
    public void reloadedSessionUsesSavedIdentityOnlyForNewEntries() {
        long session = deploy();
        FileReference saved = reload(session).get(firstVersion).addBlob("model-" + firstVersion, ByteBuffer.allocate(0));
        Map<Version, FileRegistry> registries = reload(session);
        assertEquals(saved, registries.get(firstVersion).addBlob("model-" + firstVersion, ByteBuffer.allocate(0)));
        FileReference added = registries.get(firstVersion).addBlob("new-blob", ByteBuffer.allocate(0));
        assertEquals(64, added.value().length());
        assertEquals(64, registries.get(secondVersion).addBlob("another-blob", ByteBuffer.allocate(0)).value().length());
    }

    @Test
    public void missingSavedIdentityAllowsReuseButRejectsGeneration() {
        long session = deploy();
        FileReference saved = reload(session).get(firstVersion).addBlob("model-" + firstVersion, ByteBuffer.allocate(0));
        curator.delete(SessionZooKeeperClient.getSessionPath(application.tenant(), session).append(SessionData.APPLICATION_ID_PATH));
        FileRegistry registry = reload(session).get(firstVersion);
        assertEquals(saved, registry.addBlob("model-" + firstVersion, ByteBuffer.allocate(0)));
        assertThrows(NotFoundException.class, () -> registry.addBlob("new-blob", ByteBuffer.allocate(0)));
    }

    @Test
    public void clientCanBeConstructedBeforeNewSessionStateExists() {
        SessionZooKeeperClient client = sessions.createSessionZooKeeperClient(1000);
        assertThrows(NotFoundException.class, client::readApplicationId);
        client.createNewSession(Instant.now());
        client.writeApplicationId(application);
        assertEquals(application, client.readApplicationId());
    }

    @Test
    public void unchangedRedeploymentKeepsReferences() {
        FileReference reference = reload(deploy()).get(firstVersion).addBlob("model-" + firstVersion, ByteBuffer.allocate(0));
        assertEquals(64, reference.value().length());
        assertEquals(reference, reload(deploy()).get(firstVersion).addBlob("model-" + firstVersion, ByteBuffer.allocate(0)));
        assertTrue(factory.fileDirectory().getFile(reference).isPresent());
    }

    private TestModelFactory modelFactory(Version version) {
        return new TestModelFactory(version) {
            @Override
            public ModelCreateResult createAndValidateModel(ModelContext context, ValidationParameters validation) {
                context.getFileRegistry().addBlob("model-" + version, ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
                return super.createAndValidateModel(context, validation);
            }
        };
    }

    private long deploy() {
        applications.prepareAndActivate(new File("src/test/resources/deploy/hosted-app"),
                new PrepareParams.Builder().applicationId(application).vespaVersion(firstVersion)
                        .containerEndpoints("""
                                [{"clusterId":"qrs","names":["app.example.com"],"scope":"zone","routingMethod":"sharedLayer4"}]
                                """).build());
        return applications.getActiveSession(application).orElseThrow().getSessionId();
    }

    private Map<Version, FileRegistry> reload(long session) {
        return sessions.createSessionZooKeeperClient(session).loadApplicationPackage().getFileRegistries();
    }

}
