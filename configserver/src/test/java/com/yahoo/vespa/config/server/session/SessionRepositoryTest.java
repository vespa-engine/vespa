// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

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

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public void setup() throws Exception {
        setup(new ModelFactoryRegistry(List.of(new VespaModelFactory(new NullConfigModelRegistry()))));
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
                .withFileDistributionFactory(new MockFileDistributionFactory(configserverConfig))
                .withModelFactoryRegistry(modelFactoryRegistry)
                .build();
        tenantRepository.addTenant(SessionRepositoryTest.tenantName);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
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

    // If reading a session throws an exception it should be handled and not prevent other applications
    // from loading. In this test we just show that we end up with one session in remote session
    // repo even if it had bad data (by making getSessionIdForApplication() in FailingTenantApplications
    // throw an exception).
    @Test
    public void testBadApplicationRepoOnActivate() throws Exception {
        setup();
        long sessionId = 3L;
        TenantName mytenant = TenantName.from("mytenant");
        curator.set(TenantRepository.getApplicationsPath(mytenant).append("mytenant:appX:default"), new byte[0]); // Invalid data
        tenantRepository.addTenant(mytenant);
        curator.create(TenantRepository.getSessionsPath(mytenant));
        assertThat(sessionRepository.getRemoteSessionsFromZooKeeper().size(), is(0));
        createSession(sessionId, true);
        assertThat(sessionRepository.getRemoteSessionsFromZooKeeper().size(), is(1));
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_that_new_invalid_application_throws_exception() throws Exception {
        MockModelFactory failingFactory = new MockModelFactory();
        failingFactory.vespaVersion = new Version(1, 2, 0);
        failingFactory.throwOnLoad = true;

        MockModelFactory okFactory = new MockModelFactory();
        okFactory.vespaVersion = new Version(1, 1, 0);
        okFactory.throwOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        deploy();
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
        failingFactory.vespaVersion = new Version(3, 0, 0);
        failingFactory.throwErrorOnLoad = true;

        MockModelFactory okFactory = new MockModelFactory();
        okFactory.vespaVersion = new Version(2, 0, 0);
        okFactory.throwErrorOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        File testApp = new File("src/test/apps/app-major-version-2");
        deploy(applicationId, testApp);

        // Does not cause an error because model version 3 is skipped
    }

    @Test
    public void require_that_an_application_package_can_limit_to_one_higher_major_version() throws Exception {
        MockModelFactory failingFactory = new MockModelFactory();
        failingFactory.vespaVersion = new Version(3, 0, 0);
        failingFactory.throwErrorOnLoad = true;

        MockModelFactory okFactory = new MockModelFactory();
        okFactory.vespaVersion = new Version(1, 0, 0);
        okFactory.throwErrorOnLoad = false;

        setup(new ModelFactoryRegistry(List.of(okFactory, failingFactory)));

        File testApp = new File("src/test/apps/app-major-version-2");
        deploy(applicationId, testApp);

        // Does not cause an error because model version 3 is skipped
    }

    private void createSession(long sessionId, boolean wait) {
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator,
                                                                ConfigCurator.create(curator),
                                                                tenantName,
                                                                sessionId,
                                                                ConfigUtils.getCanonicalHostName());
        zkc.createNewSession(Instant.now());
        if (wait) {
            Curator.CompletionWaiter waiter = zkc.getUploadWaiter();
            waiter.awaitCompletion(Duration.ofSeconds(120));
        }
    }

    private void assertStatusChange(long sessionId, Session.Status status) throws Exception {
        com.yahoo.path.Path statePath = sessionRepository.getSessionStatePath(sessionId);
        System.out.println("Setting and asserting state for " + statePath);
        curator.create(statePath);
        curator.framework().setData().forPath(statePath.getAbsolute(), Utf8.toBytes(status.toString()));
        assertRemoteSessionStatus(sessionId, status);
    }

    private void assertSessionRemoved(long sessionId) {
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) == null, sessionId);
        assertNull(sessionRepository.getRemoteSession(sessionId));
    }

    private void assertRemoteSessionExists(long sessionId) {
        assertRemoteSessionStatus(sessionId, Session.Status.NEW);
    }

    private void assertRemoteSessionStatus(long sessionId, Session.Status status) {
        waitFor(p -> sessionRepository.getRemoteSession(sessionId) != null &&
                     sessionRepository.getRemoteSession(sessionId).getStatus() == status, sessionId);
        assertNotNull(sessionRepository.getRemoteSession(sessionId));
        assertThat(sessionRepository.getRemoteSession(sessionId).getStatus(), is(status));
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
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId).build());
        return applicationRepository.getActiveSession(applicationId).getSessionId();
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
