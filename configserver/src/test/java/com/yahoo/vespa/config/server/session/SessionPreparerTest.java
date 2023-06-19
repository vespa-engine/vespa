// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.TestConfigDefinitionRepo;
import com.yahoo.vespa.config.server.TimeoutBudgetTest;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.zookeeper.ZKApplication;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;

import static com.yahoo.vespa.config.server.session.SessionPreparer.PrepareResult;
import static com.yahoo.vespa.config.server.session.SessionZooKeeperClient.APPLICATION_PACKAGE_REFERENCE_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SessionPreparerTest {

    private static final File testApp = new File("src/test/apps/app");
    private static final File invalidTestApp = new File("src/test/apps/illegalApp");
    private static final Version version123 = new Version(1, 2, 3);
    private static final Version version321 = new Version(3, 2, 1);
    private static final Zone zone = Zone.defaultZone();
    private final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    private final X509Certificate certificate = X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=subject"),
            Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(12345)).build();
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private MockCurator curator;
    private SessionPreparer preparer;
    private final MockSecretStore secretStore = new MockSecretStore();
    private ConfigserverConfig configserverConfig;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        curator = new MockCurator();
        configserverConfig = new ConfigserverConfig.Builder()
                .fileReferencesDir(folder.newFolder().getAbsolutePath())
                .configServerDBDir(folder.newFolder().getAbsolutePath())
                .configDefinitionsDir(folder.newFolder().getAbsolutePath())
                .build();
        preparer = createPreparer();
    }

    private SessionPreparer createPreparer() {
        return createPreparer(HostProvisionerProvider.empty());
    }

    private SessionPreparer createPreparer(HostProvisionerProvider hostProvisionerProvider) {
        ModelFactoryRegistry modelFactoryRegistry =
                new ModelFactoryRegistry(List.of(new TestModelFactory(version123), new TestModelFactory(version321)));
        return createPreparer(modelFactoryRegistry, hostProvisionerProvider);
    }

    private SessionPreparer createPreparer(ModelFactoryRegistry modelFactoryRegistry,
                                           HostProvisionerProvider hostProvisionerProvider) {
        return new SessionPreparer(
                modelFactoryRegistry,
                new MockFileDistributionFactory(configserverConfig),
                new InThreadExecutorService(),
                hostProvisionerProvider,
                configserverConfig,
                new TestConfigDefinitionRepo(),
                curator,
                zone,
                flagSource,
                secretStore);
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_that_application_validation_exception_is_not_caught() throws IOException {
        prepare(invalidTestApp);
    }

    @Test
    public void require_that_application_validation_exception_is_ignored_if_forced() throws IOException {
        prepare(invalidTestApp,
                new PrepareParams.Builder()
                        .applicationId(applicationId())
                        .ignoreValidationErrors(true)
                        .timeoutBudget(TimeoutBudgetTest.day())
                        .build(),
                1);
    }

    @Test
    public void require_that_zookeeper_is_not_written_to_if_dryrun() throws IOException {
        long sessionId = 1;
        prepare(testApp,
                new PrepareParams.Builder()
                .applicationId(applicationId())
                        .dryRun(true)
                        .timeoutBudget(TimeoutBudgetTest.day())
                        .build(),
                sessionId);
        Path sessionPath = sessionPath(sessionId);
        assertFalse(curator.exists(sessionPath.append(ZKApplication.USERAPP_ZK_SUBPATH).append("services.xml")));
    }

    @Test
    public void require_that_filedistribution_is_ignored_on_dryrun() throws IOException {
        long sessionId = 1;
        PrepareResult result = prepare(testApp,
                                       new PrepareParams.Builder()
                                               .applicationId(applicationId())
                                               .dryRun(true)
                                               .build(),
                                       sessionId);
        Map<Version, FileRegistry> fileRegistries = result.getFileRegistries();
        assertEquals(0, fileRegistries.get(version321).export().size());
    }

    @Test
    public void require_that_application_is_prepared() throws Exception {
        prepare(testApp);
        assertTrue(curator.exists(sessionPath(1).append(ZKApplication.USERAPP_ZK_SUBPATH).append("services.xml")));
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_exception_for_overlapping_host() throws IOException {
        FilesApplicationPackage app = getApplicationPackage(testApp);
        HostRegistry hostValidator = new HostRegistry();
        hostValidator.update(applicationId("foo"), Collections.singletonList("mytesthost"));
        preparer.prepare(hostValidator, new BaseDeployLogger(), new PrepareParams.Builder().applicationId(applicationId("default")).build(),
                         Optional.empty(), Instant.now(), app.getAppDir(), app, createSessionZooKeeperClient());
    }
    
    @Test
    public void require_no_warning_for_overlapping_host_for_same_appid() throws IOException {
        final StringBuilder logged = new StringBuilder();
        DeployLogger logger = (level, message) -> {
            if (level.equals(Level.WARNING) && message.contains("The host mytesthost is already in use")) logged.append("ok");
        };
        FilesApplicationPackage app = getApplicationPackage(testApp);
        HostRegistry hostValidator = new HostRegistry();
        ApplicationId applicationId = applicationId();
        hostValidator.update(applicationId, Collections.singletonList("mytesthost"));
        preparer.prepare(hostValidator, logger, new PrepareParams.Builder().applicationId(applicationId).build(),
                         Optional.empty(), Instant.now(), app.getAppDir(), app,
                         createSessionZooKeeperClient());
        assertEquals(logged.toString(), "");
    }

    @Test
    public void require_that_application_id_is_written_in_prepare() throws IOException {
        PrepareParams params = new PrepareParams.Builder().applicationId(applicationId()).build();
        int sessionId = 1;
        prepare(testApp, params);
        assertEquals(applicationId(), createSessionZooKeeperClient(sessionId).readApplicationId());
    }

    @Test
    public void require_that_writing_wrong_application_id_fails() throws IOException {
        TenantName tenant = TenantName.from("tenant");
        ApplicationId origId = new ApplicationId.Builder()
                .tenant(tenant)
                .applicationName("foo")
                .instanceName("quux")
                .build();
        PrepareParams params = new PrepareParams.Builder().applicationId(origId).build();
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Error preparing session");
        prepare(testApp, params);
    }

    @Test
    public void require_that_file_reference_of_application_package_is_written_to_zk() throws Exception {
        prepare(testApp);
        assertTrue(curator.exists(sessionPath(1).append(APPLICATION_PACKAGE_REFERENCE_PATH)));
    }

    @Test
    public void require_that_container_endpoints_are_written_and_used() throws Exception {
        var modelFactory = new TestModelFactory(version123);
        preparer = createPreparer(new ModelFactoryRegistry(List.of(modelFactory)), HostProvisionerProvider.empty());
        var endpoints = "[\n" +
                        "  {\n" +
                        "    \"clusterId\": \"foo\",\n" +
                        "    \"names\": [\n" +
                        "      \"foo.app1.tenant1.global.vespa.example.com\",\n" +
                        "      \"rotation-042.vespa.global.routing\"\n" +
                        "    ],\n" +
                        "    \"scope\": \"global\", \n" +
                        "    \"routingMethod\": \"shared\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"clusterId\": \"bar\",\n" +
                        "    \"names\": [\n" +
                        "      \"bar.app1.tenant1.global.vespa.example.com\",\n" +
                        "      \"rotation-043.vespa.global.routing\"\n" +
                        "    ],\n" +
                        "    \"scope\": \"global\",\n" +
                        "    \"routingMethod\": \"sharedLayer4\"\n" +
                        "  }\n" +
                        "]";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId)
                                                .containerEndpoints(endpoints)
                                                .build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        var expected = List.of(new ContainerEndpoint("foo",
                                                     ApplicationClusterEndpoint.Scope.global,
                                                     List.of("foo.app1.tenant1.global.vespa.example.com",
                                                             "rotation-042.vespa.global.routing"),
                                                     OptionalInt.empty(),
                                                     ApplicationClusterEndpoint.RoutingMethod.shared),
                               new ContainerEndpoint("bar",
                                                     ApplicationClusterEndpoint.Scope.global,
                                                     List.of("bar.app1.tenant1.global.vespa.example.com",
                                                             "rotation-043.vespa.global.routing"),
                                                     OptionalInt.empty(),
                                                     ApplicationClusterEndpoint.RoutingMethod.sharedLayer4));
        assertEquals(expected, readContainerEndpoints(applicationId));


        var modelContext = modelFactory.getModelContext();
        var containerEndpointsFromModel = modelContext.properties().endpoints();
        assertEquals(Set.copyOf(expected), containerEndpointsFromModel);

        // Preparing with null container endpoints keeps old value. This is what happens when deployments happen from
        // an existing session (e.g. internal redeployment).
        params = new PrepareParams.Builder().applicationId(applicationId).build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
        assertEquals(expected, readContainerEndpoints(applicationId));

        // Preparing with empty container endpoints clears endpoints
        params = new PrepareParams.Builder().applicationId(applicationId).containerEndpoints("[]").build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
        assertEquals(List.of(), readContainerEndpoints(applicationId));
    }

    @Test
    public void require_that_endpoint_certificate_metadata_is_written() throws IOException {
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).endpointCertificateMetadata("{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 7}").build();
        secretStore.put("vespa.tlskeys.tenant1--app1-cert", 7, X509CertificateUtils.toPem(certificate));
        secretStore.put("vespa.tlskeys.tenant1--app1-key", 7, KeyUtils.toPem(keyPair.getPrivate()));
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        // Read from zk and verify cert and key are available
        Path tenantPath = TenantRepository.getTenantPath(applicationId.tenant());
        Optional<EndpointCertificateSecrets> endpointCertificateSecrets = new EndpointCertificateMetadataStore(curator, tenantPath)
                .readEndpointCertificateMetadata(applicationId)
                .flatMap(p -> new EndpointCertificateRetriever(secretStore).readEndpointCertificateSecrets(p));

        assertTrue(endpointCertificateSecrets.isPresent());
        assertTrue(endpointCertificateSecrets.get().key().startsWith("-----BEGIN EC PRIVATE KEY"));
        assertTrue(endpointCertificateSecrets.get().certificate().startsWith("-----BEGIN CERTIFICATE"));
    }

    @Test(expected = CertificateNotReadyException.class)
    public void endpoint_certificate_is_missing_when_not_in_secretstore() throws IOException {
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).endpointCertificateMetadata("{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 7}").build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    @Test(expected = CertificateNotReadyException.class)
    public void endpoint_certificate_is_missing_when_certificate_not_in_secretstore() throws IOException {
        var tlskey = "vespa.tlskeys.tenant1--app1";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).endpointCertificateMetadata("{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 7}").build();
        secretStore.put(tlskey+"-key", 7, "KEY");
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    @Test(expected = LoadBalancerServiceException.class)
    public void require_that_conflict_is_returned_when_creating_load_balancer_fails() throws IOException {
        var configserverConfig = new ConfigserverConfig.Builder().hostedVespa(true).build();
        MockProvisioner provisioner = new MockProvisioner().transientFailureOnPrepare();
        preparer = createPreparer(HostProvisionerProvider.withProvisioner(provisioner, configserverConfig));
        var params = new PrepareParams.Builder().applicationId(applicationId("test")).build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    @Test
    public void require_that_cloud_account_is_written() throws Exception {
        TestModelFactory modelFactory = new TestModelFactory(version123);
        preparer = createPreparer(new ModelFactoryRegistry(List.of(modelFactory)), HostProvisionerProvider.empty());
        ApplicationId applicationId = applicationId("test");
        CloudAccount expected = CloudAccount.from("012345678912");
        PrepareParams params = new PrepareParams.Builder().applicationId(applicationId)
                                                          .cloudAccount(expected)
                                                          .build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        SessionZooKeeperClient zkClient = createSessionZooKeeperClient();
        assertEquals(expected, zkClient.readCloudAccount().get());

        ModelContext modelContext = modelFactory.getModelContext();
        Optional<CloudAccount> accountFromModel = modelContext.properties().cloudAccount();
        assertEquals(Optional.of(expected), accountFromModel);
    }

    @Test
    public void require_that_dataplane_tokens_are_written() throws Exception {
        TestModelFactory modelFactory = new TestModelFactory(version123);
        preparer = createPreparer(new ModelFactoryRegistry(List.of(modelFactory)), HostProvisionerProvider.empty());
        ApplicationId applicationId = applicationId("test");
        List<DataplaneToken> expected = List.of(new DataplaneToken("id", List.of(new DataplaneToken.Version("f1", "ch1"))));
        PrepareParams params = new PrepareParams.Builder().applicationId(applicationId)
                .dataplaneTokens(expected)
                .build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        SessionZooKeeperClient zkClient = createSessionZooKeeperClient();
        assertEquals(expected, zkClient.readDataplaneTokens());

        ModelContext modelContext = modelFactory.getModelContext();
        List<DataplaneToken> tokensFromModel = modelContext.properties().dataplaneTokens();
        assertEquals(expected, tokensFromModel);
    }

    private List<ContainerEndpoint> readContainerEndpoints(ApplicationId applicationId) {
        Path tenantPath = TenantRepository.getTenantPath(applicationId.tenant());
        return new ContainerEndpointsCache(tenantPath, curator).read(applicationId);
    }

    private void prepare(File app) throws IOException {
        prepare(app, new PrepareParams.Builder().applicationId(applicationId()).build());
    }

    private void prepare(File app, PrepareParams params) throws IOException {
        prepare(app, params, 1);
    }

    private PrepareResult prepare(File app, PrepareParams params, long sessionId) throws IOException {
        FilesApplicationPackage applicationPackage = getApplicationPackage(app);
        return preparer.prepare(new HostRegistry(), getLogger(), params,
                                Optional.empty(), Instant.now(), applicationPackage.getAppDir(),
                                applicationPackage, createSessionZooKeeperClient(sessionId));
    }

    private FilesApplicationPackage getApplicationPackage(File testFile) throws IOException {
        File appDir = folder.newFolder();
        IOUtils.copyDirectory(testFile, appDir);
        return FilesApplicationPackage.fromFile(appDir);
    }

    private DeployHandlerLogger getLogger() {
        return DeployHandlerLogger.forApplication(
                new ApplicationId.Builder().tenant("testtenant").applicationName("testapp").build(), false /*verbose */);
    }


    private ApplicationId applicationId() {
        return ApplicationId.from(TenantName.defaultName(), ApplicationName.from("default"), InstanceName.defaultName());
    }

    private ApplicationId applicationId(String applicationName) {
        return ApplicationId.from(TenantName.defaultName(),
                                  ApplicationName.from(applicationName), InstanceName.defaultName());
    }

    private SessionZooKeeperClient createSessionZooKeeperClient() {
        return createSessionZooKeeperClient(1);
    }

    private SessionZooKeeperClient createSessionZooKeeperClient(long sessionId) {
        return new SessionZooKeeperClient(curator, applicationId().tenant(), sessionId, configserverConfig);
    }

    private Path sessionPath(long sessionId) {
        return TenantRepository.getSessionsPath(applicationId().tenant()).append(String.valueOf(sessionId));
    }

}
