// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class SessionRepositoryTest {

    private static final TenantName tenantName = TenantName.defaultName();
    private static final ApplicationId applicationId = ApplicationId.from(tenantName.value(), "testApp", "default");
    private static final File testApp = new File("src/test/apps/app");
    private static final File appJdiscOnly = new File("src/test/apps/app-jdisc-only");

    private MockCurator curator;
    private TenantRepository tenantRepository;
    private ApplicationRepository applicationRepository;
    private SessionRepository sessionRepository;
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public void setup() throws Exception {
        setup(new ModelFactoryRegistry(List.of(VespaModelFactory.createTestFactory())));
    }

    private void setup(ModelFactoryRegistry modelFactoryRegistry) throws Exception {
        curator = new MockCurator();
        File configserverDbDir = temporaryFolder.newFolder().getAbsoluteFile();
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(configserverDbDir.getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .sessionLifetime(5)
                .build();
        tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .withCurator(curator)
                .withFlagSource(flagSource)
                .withFileDistributionFactory(new MockFileDistributionFactory(configserverConfig))
                .withModelFactoryRegistry(modelFactoryRegistry)
                .build();
        tenantRepository.addTenant(SessionRepositoryTest.tenantName);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(new OrchestratorMock())
                .withFlagSource(flagSource)
                .build();
        sessionRepository = tenantRepository.getTenant(tenantName).getSessionRepository();
    }

    @Test
    public void require_that_local_sessions_are_created_and_deleted() throws Exception {
        setup();
        long firstSessionId = deploy();
        long secondSessionId = deploy();
        assertNotNull(sessionRepository.getLocalSession(firstSessionId));
        assertNotNull(sessionRepository.getLocalSession(secondSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId + 1));

        ApplicationSet applicationSet = sessionRepository.ensureApplicationLoaded(sessionRepository.getRemoteSession(firstSessionId));
        assertNotNull(applicationSet);
        assertEquals(2, applicationSet.getApplicationGeneration());
        assertEquals(applicationId.application(), applicationSet.getForVersionOrLatest(Optional.empty(), Instant.now()).getId().application());
        assertNotNull(applicationSet.getForVersionOrLatest(Optional.empty(), Instant.now()).getModel());

        LocalSession session = sessionRepository.getLocalSession(secondSessionId);
        Collection<NamedReader> a = session.applicationPackage.get().getSchemas();
        assertEquals(1, a.size());

        sessionRepository.close();
        // All created sessions are deleted
        assertNull(sessionRepository.getLocalSession(firstSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId));
    }

    @Test
    public void require_that_local_sessions_belong_to_a_tenant() throws Exception {
        setup();
        // tenant is "default"

        long firstSessionId = deploy();
        long secondSessionId = deploy();
        assertNotNull(sessionRepository.getLocalSession(firstSessionId));
        assertNotNull(sessionRepository.getLocalSession(secondSessionId));
        assertNull(sessionRepository.getLocalSession(secondSessionId + 1));

        // tenant is "newTenant"
        TenantName newTenant = TenantName.from("newTenant");
        tenantRepository.addTenant(newTenant);
        long sessionId = deploy(ApplicationId.from(newTenant.value(), "testapp", "default"), appJdiscOnly);
        SessionRepository sessionRepository2 = tenantRepository.getTenant(newTenant).getSessionRepository();
        assertNotNull(sessionRepository2.getLocalSession(sessionId));
    }

    @Test
    public void testInitialize() throws Exception {
        setup();
        createSession(10L, false);
        createSession(11L, false);
        assertRemoteSessionExists(10L);
        assertRemoteSessionExists(11L);
    }

    @Test
    public void testSessionStateChange() throws Exception {
        setup();
        long sessionId = 3L;
        createSession(sessionId, true);
        assertRemoteSessionStatus(sessionId, Session.Status.NEW);
        assertStatusChange(sessionId, Session.Status.PREPARE);
        assertStatusChange(sessionId, Session.Status.ACTIVATE);
    }

    // If reading a session throws an exception when bootstrapping SessionRepository it should fail,
    // to make sure config server does not comes up and serves invalid/old config or, if this is hosted,
    // serves empty config (takes down services on all nodes belonging to an application)
    @Test
    public void testInvalidSessionWhenBootstrappingSessionRepo() throws Exception {
        setup();

        // Create a session with invalid data and set active session for application to this session
        String sessionIdString = "3";
        Path sessionPath = TenantRepository.getSessionsPath(tenantName).append(sessionIdString);
        curator.create(sessionPath);
        curator.set(sessionPath.append("applicationId"), new byte[0]); // Invalid data
        Path applicationsPath = TenantRepository.getApplicationsPath(tenantName);
        curator.set(applicationsPath.append(applicationId.serializedForm()), Utf8.toBytes(sessionIdString));

        expectedException.expectMessage("Could not load remote session " + sessionIdString);
        expectedException.expect(RuntimeException.class);
        sessionRepository.loadSessions(new InThreadExecutorService());
        assertTrue(sessionRepository.getRemoteSessionsFromZooKeeper().isEmpty());
    }

    @Test
    public void require_that_new_invalid_application_throws_exception() throws Exception {
        MockModelFactory failingFactory = new MockModelFactory();
        failingFactory.vespaVersion = new Version(1, 2, 0);
        failingFactory.throwOnLoad = true;

        MockModelFactory okFactory = new MockModelFactory();
        okFactory.vespaVersion = new Version(1, 1, 0);
        okFactory.throwOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        Collection<LocalSession> sessions = sessionRepository.getLocalSessions();
        try {
            deploy();
            fail("deployment should have failed");
        } catch (InvalidApplicationException e) {
            assertEquals(sessions, sessionRepository.getLocalSessions());
        }
    }

    @Test
    public void require_that_old_invalid_application_does_not_throw_exception_if_skipped_also_across_major_versions() throws Exception {
        MockModelFactory failingFactory = new MockModelFactory();
        failingFactory.vespaVersion = new Version(1, 0, 0);
        failingFactory.throwOnLoad = true;

        MockModelFactory okFactory =
                new MockModelFactory("<validation-overrides><allow until='2000-01-30'>skip-old-config-models</allow></validation-overrides>");
        okFactory.vespaVersion = new Version(2, 0, 0);
        okFactory.throwOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        deploy();
    }

    @Test
    public void require_that_an_application_package_can_limit_to_one_major_version() throws Exception {
        MockModelFactory failingFactory = new MockModelFactory();
        failingFactory.vespaVersion = new Version(8, 0, 0);
        failingFactory.throwErrorOnLoad = true;

        MockModelFactory okFactory = new MockModelFactory();
        okFactory.vespaVersion = new Version(7, 0, 0);
        okFactory.throwErrorOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        File testApp = new File("src/test/apps/app-major-version-7");
        deploy(new PrepareParams.Builder().applicationId(applicationId).vespaVersion(okFactory.vespaVersion).build(), testApp);

        // Does not cause an error because model version 8 is skipped
    }

    @Test
    public void require_that_searchdefinitions_are_written_to_schemas_dir() throws Exception {
        setup();

        long sessionId = deploy(applicationId, new File("src/test/apps/deprecated-features-app"));
        LocalSession session = sessionRepository.getLocalSession(sessionId);

        assertEquals(1, session.applicationPackage.get().getSchemas().size());

        ApplicationFile schema = getSchema(session, "schemas");
        assertTrue(schema.exists());
        ApplicationFile sd = getSchema(session, "searchdefinitions");
        assertFalse(sd.exists());
    }

    ApplicationFile getSchema(Session session, String subDirectory) {
        return session.applicationPackage.get().getFile(Path.fromString(subDirectory).append("music.sd"));
    }

    private void createSession(long sessionId, boolean wait) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator,
                                                                tenantName,
                                                                sessionId,
                                                                applicationRepository.configserverConfig());
        zkc.createNewSession(Instant.now());
        if (wait)
            zkc.getUploadWaiter().awaitCompletion(Duration.ofSeconds(120));
    }

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        com.yahoo.path.Path statePath = sessionRepository.getSessionStatePath(sessionId);
        System.out.println("Setting and asserting state for " + statePath);
        curator.create(statePath);
        curator.framework().setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        assertRemoteSessionStatus(sessionId, status);
    }

    private void assertRemoteSessionExists(long sessionId) {
        assertRemoteSessionStatus(sessionId, Session.Status.NEW);
    }

    private void assertRemoteSessionStatus(long sessionId, Session.Status status) {
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) != null &&
                     sessionRepository.getRemoteSession(sessionId).getStatus() == status, sessionId);
        assertNotNull(sessionRepository.getRemoteSession(sessionId));
        assertEquals(status, sessionRepository.getRemoteSession(sessionId).getStatus());
    }

    private void waitFor(LongPredicate predicate, long sessionId) {
        long endTime = System.currentTimeMillis() + 5_000;
        boolean ok;
        do {
            ok = predicate.test(sessionId);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (System.currentTimeMillis() < endTime && !ok);
    }

    private long deploy() {
        return deploy(applicationId);
    }

    private long deploy(ApplicationId applicationId) {
        return deploy(applicationId, testApp);
    }

    private long deploy(ApplicationId applicationId, File testApp) {
        return deploy(new PrepareParams.Builder().applicationId(applicationId).build(), testApp);
    }

    private long deploy(PrepareParams params, File testApp) {
        applicationRepository.deploy(testApp, params);
        return applicationRepository.getActiveSession(params.getApplicationId()).get().getSessionId();
    }

    private static class MockModelFactory implements ModelFactory {

        /** Throw a RuntimeException on load - this is handled gracefully during model building */
        boolean throwOnLoad = false;

        /** Throw an Error on load - this is useful to propagate this condition all the way to the test */
        boolean throwErrorOnLoad = false;

        ModelContext modelContext;
        public Version vespaVersion = new Version(1, 2, 3);

        /** The validation overrides of this, or null if none */
        private final String validationOverrides;

        private final Clock clock = Clock.fixed(LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

        MockModelFactory() { this(null); }

        MockModelFactory(String validationOverrides) {
            this.validationOverrides = validationOverrides;
        }

        @Override
        public Version version() {
            return vespaVersion;
        }

        /** Returns the clock used by this, which is fixed at the instant 2000-01-01T00:00:00 */
        public Clock clock() { return clock; }

        @Override
        public Model createModel(ModelContext modelContext) {
            if (throwErrorOnLoad)
                throw new Error("error on load");
            if (throwOnLoad)
                throw new IllegalArgumentException("exception on load");
            this.modelContext = modelContext;
            return loadModel();
        }

        Model loadModel() {
            try {
                ApplicationPackage application = new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().withValidationOverrides(validationOverrides).build();
                DeployState deployState = new DeployState.Builder().applicationPackage(application).now(clock.instant()).build();
                return new VespaModel(deployState);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            if (throwErrorOnLoad)
                throw new Error("error on load");
            if (throwOnLoad)
                throw new IllegalArgumentException("exception on load");
            this.modelContext = modelContext;
            return new ModelCreateResult(loadModel(), new ArrayList<>());
        }
    }

}
