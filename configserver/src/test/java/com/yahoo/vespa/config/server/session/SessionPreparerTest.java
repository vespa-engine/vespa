// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.*;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.configchange.MockRestartAction;
import com.yahoo.vespa.config.server.configchange.RestartActions;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.Rotations;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.1
 */
public class SessionPreparerTest extends TestWithCurator {

    private static final Path tenantPath = Path.createRoot();
    private static final Path sessionsPath = tenantPath.append("sessions").append("testapp");
    private static final File testApp = new File("src/test/apps/app");
    private static final File invalidTestApp = new File("src/test/apps/illegalApp");

    private SessionPreparer preparer;
    private TestComponentRegistry componentRegistry;
    private MockFileDistributionFactory fileDistributionFactory;


    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        componentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        fileDistributionFactory = (MockFileDistributionFactory)componentRegistry.getFileDistributionFactory();
        preparer = createPreparer();
    }

    private SessionPreparer createPreparer() {
        return createPreparer(HostProvisionerProvider.empty());
    }

    private SessionPreparer createPreparer(HostProvisionerProvider hostProvisionerProvider) {
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Arrays.asList(
                new TestModelFactory(Version.fromIntValues(1, 2, 3)),
                new TestModelFactory(Version.fromIntValues(3, 2, 1))));
        return createPreparer(modelFactoryRegistry, hostProvisionerProvider);
    }

    private SessionPreparer createPreparer(ModelFactoryRegistry modelFactoryRegistry,
                                           HostProvisionerProvider hostProvisionerProvider) {
        return new SessionPreparer(
                modelFactoryRegistry,
                componentRegistry.getFileDistributionFactory(),
                hostProvisionerProvider,
                new PermanentApplicationPackage(componentRegistry.getConfigserverConfig()),
                componentRegistry.getConfigserverConfig(),
                componentRegistry.getConfigDefinitionRepo(),
                curator,
                componentRegistry.getZone());
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_that_application_validation_exception_is_not_caught() throws IOException, SAXException {
        FilesApplicationPackage app = getApplicationPackage(invalidTestApp);
        preparer.prepare(getContext(app), getLogger(), new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
    }

    @Test
    public void require_that_application_validation_exception_is_ignored_if_forced() throws IOException, SAXException {
        FilesApplicationPackage app = getApplicationPackage(invalidTestApp);
        preparer.prepare(getContext(app), getLogger(),
                         new PrepareParams.Builder().ignoreValidationErrors(true).timeoutBudget(TimeoutBudgetTest.day()).build(),
                         Optional.empty(), tenantPath, Instant.now());
    }

    @Test
    public void require_that_zookeeper_is_not_written_to_if_dryrun() throws IOException {
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(),
                         new PrepareParams.Builder().dryRun(true).timeoutBudget(TimeoutBudgetTest.day()).build(),
                         Optional.empty(), tenantPath, Instant.now());
        assertFalse(configCurator.exists(sessionsPath.append(ConfigCurator.USERAPP_ZK_SUBPATH).append("services.xml").getAbsolute()));
    }

    @Test
    public void require_that_filedistribution_is_ignored_on_dryrun() throws IOException {
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(),
                                                       new PrepareParams.Builder().dryRun(true).timeoutBudget(TimeoutBudgetTest.day()).build(),
                                                       Optional.empty(), tenantPath, Instant.now());
        assertThat(fileDistributionFactory.mockFileDistributionProvider.timesCalled, is(0));
    }

    @Test
    public void require_that_application_is_prepared() throws Exception {
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(), new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
        assertThat(fileDistributionFactory.mockFileDistributionProvider.timesCalled, is(2));
        assertTrue(configCurator.exists(sessionsPath.append(ConfigCurator.USERAPP_ZK_SUBPATH).append("services.xml").getAbsolute()));
    }

    @Test
    public void require_that_prepare_succeeds_if_newer_version_fails() throws IOException {
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Arrays.asList(
                new TestModelFactory(Version.fromIntValues(1, 2, 3)),
                new FailingModelFactory(Version.fromIntValues(3, 2, 1), new IllegalArgumentException("BOOHOO"))));
        preparer = createPreparer(modelFactoryRegistry, HostProvisionerProvider.empty());
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(), new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_that_prepare_fails_if_older_version_fails() throws IOException {
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Arrays.asList(
                new TestModelFactory(Version.fromIntValues(3, 2, 3)),
                new FailingModelFactory(Version.fromIntValues(1, 2, 1), new IllegalArgumentException("BOOHOO"))));
        preparer = createPreparer(modelFactoryRegistry, HostProvisionerProvider.empty());
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(), new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_exception_for_overlapping_host() throws IOException {
        SessionContext ctx = getContext(getApplicationPackage(testApp));
        ((HostRegistry<ApplicationId>)ctx.getHostValidator()).update(applicationId("foo"), Collections.singletonList("mytesthost"));
        preparer.prepare(ctx, new BaseDeployLogger(), new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
    }
    
    @Test
    public void require_no_warning_for_overlapping_host_for_same_appid() throws IOException {
        SessionContext ctx = getContext(getApplicationPackage(testApp));
        ((HostRegistry<ApplicationId>)ctx.getHostValidator()).update(applicationId("default"), Collections.singletonList("mytesthost"));
        final StringBuilder logged = new StringBuilder();
        DeployLogger logger = (level, message) -> {
            System.out.println(level + ": "+message);
            if (level.equals(LogLevel.WARNING) && message.contains("The host mytesthost is already in use")) logged.append("ok");
        };
        preparer.prepare(ctx, logger, new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now());
        assertEquals(logged.toString(), "");
    }

    @Test
    public void require_that_application_id_is_written_in_prepare() throws IOException {
        TenantName tenant = TenantName.from("tenant");
        ApplicationId origId = new ApplicationId.Builder()
                               .tenant(tenant)
                               .applicationName("foo").instanceName("quux").build();
        PrepareParams params = new PrepareParams.Builder().applicationId(origId).build();
        preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(), params, Optional.empty(), tenantPath, Instant.now());
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, sessionsPath);
        assertTrue(configCurator.exists(sessionsPath.append(SessionZooKeeperClient.APPLICATION_ID_PATH).getAbsolute()));
        assertThat(zkc.readApplicationId(), is(origId));
    }

    @Test
    public void require_that_config_change_actions_are_collected_from_all_models() throws IOException {
        ServiceInfo service = new ServiceInfo("serviceName", "serviceType", null, new HashMap<>(), "configId", "hostName");
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Arrays.asList(
                new ConfigChangeActionsModelFactory(Version.fromIntValues(1, 2, 3),
                        new MockRestartAction("change", Arrays.asList(service))),
                new ConfigChangeActionsModelFactory(Version.fromIntValues(1, 2, 4),
                        new MockRestartAction("other change", Arrays.asList(service)))));
        preparer = createPreparer(modelFactoryRegistry, HostProvisionerProvider.empty());
        List<RestartActions.Entry> actions =
                preparer.prepare(getContext(getApplicationPackage(testApp)), getLogger(),
                                 new PrepareParams.Builder().build(), Optional.empty(), tenantPath, Instant.now())
                        .getRestartActions().getEntries();
        assertThat(actions.size(), is(1));
        assertThat(actions.get(0).getMessages(), equalTo(ImmutableSet.of("change", "other change")));
    }

    private Set<Rotation> readRotationsFromZK(ApplicationId applicationId) {
        return new Rotations(curator, tenantPath).readRotationsFromZooKeeper(applicationId);
    }

    @Test
    public void require_that_rotations_are_written_in_prepare() throws IOException {
        final String rotations = "mediasearch.msbe.global.vespa.yahooapis.com";
        final ApplicationId applicationId = applicationId("test");
        PrepareParams params = new PrepareParams.Builder().applicationId(applicationId).rotations(rotations).build();
        File app = new File("src/test/resources/deploy/app");
        preparer.prepare(getContext(getApplicationPackage(app)), getLogger(), params, Optional.empty(), tenantPath, Instant.now());
        assertThat(readRotationsFromZK(applicationId), contains(new Rotation(rotations)));
    }

    @Test
    public void require_that_rotations_are_read_from_zookeeper_and_used() throws IOException {
        final Version vespaVersion = Version.fromIntValues(1, 2, 3);
        final TestModelFactory modelFactory = new TestModelFactory(vespaVersion);
        preparer = createPreparer(new ModelFactoryRegistry(Arrays.asList(modelFactory)),
                HostProvisionerProvider.empty());

        final String rotations = "foo.msbe.global.vespa.yahooapis.com";
        final ApplicationId applicationId = applicationId("test");
        new Rotations(curator, tenantPath).writeRotationsToZooKeeper(applicationId, Collections.singleton(new Rotation(rotations)));
        final PrepareParams params = new PrepareParams.Builder().applicationId(applicationId).build();
        final File app = new File("src/test/resources/deploy/app");
        preparer.prepare(getContext(getApplicationPackage(app)), getLogger(), params, Optional.empty(), tenantPath, Instant.now());

        // check that the rotation from zookeeper were used
        final ModelContext modelContext = modelFactory.getModelContext();
        final Set<Rotation> rotationSet = modelContext.properties().rotations();
        assertThat(rotationSet, contains(new Rotation(rotations)));

        // Check that the persisted value is still the same
        assertThat(readRotationsFromZK(applicationId), contains(new Rotation(rotations)));
    }

    private SessionContext getContext(FilesApplicationPackage app) throws IOException {
        return new SessionContext(app, new SessionZooKeeperClient(curator, sessionsPath), app.getAppDir(), new MemoryTenantApplications(), new HostRegistry<>(), new SuperModelGenerationCounter(curator));
    }

    private FilesApplicationPackage getApplicationPackage(File testFile) throws IOException {
        File appDir = folder.newFolder();
        IOUtils.copyDirectory(testFile, appDir);
        return FilesApplicationPackage.fromFile(appDir);
    }

    DeployHandlerLogger getLogger() {
        return getLogger(false);
    }

    DeployHandlerLogger getLogger(boolean verbose) {
        return new DeployHandlerLogger(new Slime().get(), verbose,
                                       new ApplicationId.Builder().tenant("testtenant").applicationName("testapp").build());
    }

    private static class FailingModelFactory extends TestModelFactory {
        private final RuntimeException exception;
        public FailingModelFactory(Version vespaVersion, RuntimeException exception) {
            super(vespaVersion);
            this.exception = exception;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors) {
            throw exception;
        }
    }

    private ApplicationId applicationId(String applicationName) {
        return ApplicationId.from(TenantName.defaultName(),
                                  ApplicationName.from(applicationName), InstanceName.defaultName());
    }

    private static class ConfigChangeActionsModelFactory extends TestModelFactory {
        private final ConfigChangeAction action;
        public ConfigChangeActionsModelFactory(Version vespaVersion, ConfigChangeAction action) {
            super(vespaVersion);
            this.action = action;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors) {
            ModelCreateResult result = super.createAndValidateModel(modelContext, ignoreValidationErrors);
            return new ModelCreateResult(result.getModel(), Arrays.asList(action));
        }
    }
}
