// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Slime;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.MockReloadHandler;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TimeoutBudgetTest;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.model.TestModelFactory;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.ContainerEndpointsCache;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateMetadataStore;
import com.yahoo.vespa.config.server.tenant.EndpointCertificateRetriever;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import static com.yahoo.vespa.config.server.session.SessionZooKeeperClient.APPLICATION_PACKAGE_REFERENCE_PATH;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class SessionPreparerTest {

    private static final Path tenantPath = Path.createRoot();
    private static final Path sessionsPath = tenantPath.append("sessions").append("testapp");
    private static final File testApp = new File("src/test/apps/app");
    private static final File invalidTestApp = new File("src/test/apps/illegalApp");
    private static final Version version123 = new Version(1, 2, 3);
    private static final Version version321 = new Version(3, 2, 1);
    private KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
    private X509Certificate certificate = X509CertificateBuilder.fromKeypair(keyPair, new X500Principal("CN=subject"),
            Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), SignatureAlgorithm.SHA512_WITH_ECDSA, BigInteger.valueOf(12345)).build();

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private MockCurator curator;
    private ConfigCurator configCurator;
    private SessionPreparer preparer;
    private TestComponentRegistry componentRegistry;
    private MockFileDistributionFactory fileDistributionFactory;
    private MockSecretStore secretStore = new MockSecretStore();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() {
        curator = new MockCurator();
        configCurator = ConfigCurator.create(curator);
        componentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        fileDistributionFactory = (MockFileDistributionFactory)componentRegistry.getFileDistributionProvider();
        preparer = createPreparer();
    }

    private SessionPreparer createPreparer() {
        return createPreparer(HostProvisionerProvider.empty());
    }

    private SessionPreparer createPreparer(HostProvisionerProvider hostProvisionerProvider) {
        ModelFactoryRegistry modelFactoryRegistry =
                new ModelFactoryRegistry(Arrays.asList(new TestModelFactory(version123), new TestModelFactory(version321)));
        return createPreparer(modelFactoryRegistry, hostProvisionerProvider);
    }

    private SessionPreparer createPreparer(ModelFactoryRegistry modelFactoryRegistry,
                                           HostProvisionerProvider hostProvisionerProvider) {
        return new SessionPreparer(
                modelFactoryRegistry,
                componentRegistry.getFileDistributionProvider(),
                hostProvisionerProvider,
                new PermanentApplicationPackage(componentRegistry.getConfigserverConfig()),
                componentRegistry.getConfigserverConfig(),
                componentRegistry.getStaticConfigDefinitionRepo(),
                curator,
                componentRegistry.getZone(),
                flagSource,
                secretStore);
    }

    @Test(expected = InvalidApplicationException.class)
    public void require_that_application_validation_exception_is_not_caught() throws IOException {
        prepare(invalidTestApp);
    }

    @Test
    public void require_that_application_validation_exception_is_ignored_if_forced() throws IOException {
        prepare(invalidTestApp, new PrepareParams.Builder().ignoreValidationErrors(true).timeoutBudget(TimeoutBudgetTest.day()).build());
    }

    @Test
    public void require_that_zookeeper_is_not_written_to_if_dryrun() throws IOException {
        prepare(testApp, new PrepareParams.Builder().dryRun(true).timeoutBudget(TimeoutBudgetTest.day()).build());
        assertFalse(configCurator.exists(sessionsPath.append(ConfigCurator.USERAPP_ZK_SUBPATH).append("services.xml").getAbsolute()));
    }

    @Test
    public void require_that_filedistribution_is_ignored_on_dryrun() throws IOException {
        prepare(testApp, new PrepareParams.Builder().dryRun(true).timeoutBudget(TimeoutBudgetTest.day()).build());
        assertThat(fileDistributionFactory.mockFileDistributionProvider.timesCalled, is(0));
    }

    @Test
    public void require_that_application_is_prepared() throws Exception {
        prepare(testApp);
        assertThat(fileDistributionFactory.mockFileDistributionProvider.timesCalled, is(1)); // Only builds the newest version
        assertTrue(configCurator.exists(sessionsPath.append(ConfigCurator.USERAPP_ZK_SUBPATH).append("services.xml").getAbsolute()));
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
            if (level.equals(Level.WARNING) && message.contains("The host mytesthost is already in use")) logged.append("ok");
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
        prepare(testApp, params);
        SessionZooKeeperClient zkc = new SessionZooKeeperClient(curator, sessionsPath);
        assertTrue(configCurator.exists(sessionsPath.append(SessionZooKeeperClient.APPLICATION_ID_PATH).getAbsolute()));
        assertThat(zkc.readApplicationId(), is(origId));
    }

    @Test
    public void require_that_file_reference_of_application_package_is_written_to_zk() throws Exception {
        flagSource.withBooleanFlag(Flags.CONFIGSERVER_DISTRIBUTE_APPLICATION_PACKAGE.id(), true);
        prepare(testApp);
        assertTrue(configCurator.exists(sessionsPath.append(APPLICATION_PACKAGE_REFERENCE_PATH).getAbsolute()));
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
                        "    ]\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"clusterId\": \"bar\",\n" +
                        "    \"names\": [\n" +
                        "      \"bar.app1.tenant1.global.vespa.example.com\",\n" +
                        "      \"rotation-043.vespa.global.routing\"\n" +
                        "    ]\n" +
                        "  }\n" +
                        "]";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId)
                                                .containerEndpoints(endpoints)
                                                .build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        var expected = List.of(new ContainerEndpoint("foo",
                                                     List.of("foo.app1.tenant1.global.vespa.example.com",
                                                             "rotation-042.vespa.global.routing")),
                               new ContainerEndpoint("bar",
                                                     List.of("bar.app1.tenant1.global.vespa.example.com",
                                                             "rotation-043.vespa.global.routing")));
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
    public void require_that_tlssecretkey_is_written() throws IOException {
        var tlskey = "vespa.tlskeys.tenant1--app1";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).tlsSecretsKeyName(tlskey).build();

        secretStore.put("vespa.tlskeys.tenant1--app1-cert", X509CertificateUtils.toPem(certificate));
        secretStore.put("vespa.tlskeys.tenant1--app1-key", KeyUtils.toPem(keyPair.getPrivate()));

        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        // Read from zk and verify cert and key are available
        Optional<EndpointCertificateSecrets> endpointCertificateSecrets = new EndpointCertificateMetadataStore(curator, tenantPath)
                .readEndpointCertificateMetadata(applicationId)
                .flatMap(p -> new EndpointCertificateRetriever(secretStore).readEndpointCertificateSecrets(p));
        assertTrue(endpointCertificateSecrets.isPresent());
        assertTrue(endpointCertificateSecrets.get().key().startsWith("-----BEGIN EC PRIVATE KEY"));
        assertTrue(endpointCertificateSecrets.get().certificate().startsWith("-----BEGIN CERTIFICATE"));
    }

    @Test
    public void require_that_endpoint_certificate_metadata_is_written() throws IOException {
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).endpointCertificateMetadata("{\"keyName\": \"vespa.tlskeys.tenant1--app1-key\", \"certName\":\"vespa.tlskeys.tenant1--app1-cert\", \"version\": 7}").build();
        secretStore.put("vespa.tlskeys.tenant1--app1-cert", 7, X509CertificateUtils.toPem(certificate));
        secretStore.put("vespa.tlskeys.tenant1--app1-key", 7, KeyUtils.toPem(keyPair.getPrivate()));
        prepare(new File("src/test/resources/deploy/hosted-app"), params);

        // Read from zk and verify cert and key are available
        Optional<EndpointCertificateSecrets> endpointCertificateSecrets = new EndpointCertificateMetadataStore(curator, tenantPath)
                .readEndpointCertificateMetadata(applicationId)
                .flatMap(p -> new EndpointCertificateRetriever(secretStore).readEndpointCertificateSecrets(p));

        assertTrue(endpointCertificateSecrets.isPresent());
        assertTrue(endpointCertificateSecrets.get().key().startsWith("-----BEGIN EC PRIVATE KEY"));
        assertTrue(endpointCertificateSecrets.get().certificate().startsWith("-----BEGIN CERTIFICATE"));
    }

    @Test(expected = CertificateNotReadyException.class)
    public void require_that_tlssecretkey_is_missing_when_not_in_secretstore() throws IOException {
        var tlskey = "vespa.tlskeys.tenant1--app1";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).tlsSecretsKeyName(tlskey).build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    @Test(expected = CertificateNotReadyException.class)
    public void require_that_tlssecretkey_is_missing_when_certificate_not_in_secretstore() throws IOException {
        var tlskey = "vespa.tlskeys.tenant1--app1";
        var applicationId = applicationId("test");
        var params = new PrepareParams.Builder().applicationId(applicationId).tlsSecretsKeyName(tlskey).build();
        secretStore.put(tlskey+"-key", "KEY");
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    @Test(expected = LoadBalancerServiceException.class)
    public void require_that_conflict_is_returned_when_creating_load_balancer_fails() throws IOException {
        preparer = createPreparer(HostProvisionerProvider.withProvisioner(new FailWithTransientExceptionProvisioner()));
        var params = new PrepareParams.Builder().applicationId(applicationId("test")).build();
        prepare(new File("src/test/resources/deploy/hosted-app"), params);
    }

    private List<ContainerEndpoint> readContainerEndpoints(ApplicationId application) {
        return new ContainerEndpointsCache(tenantPath, curator).read(application);
    }

    private void prepare(File app) throws IOException {
        prepare(app, new PrepareParams.Builder().build());
    }

    private void prepare(File app, PrepareParams params) throws IOException {
        preparer.prepare(getContext(getApplicationPackage(app)), getLogger(), params, Optional.empty(), tenantPath, Instant.now());
    }

    private SessionContext getContext(FilesApplicationPackage app) throws IOException {
        return new SessionContext(app,
                                  new SessionZooKeeperClient(curator, sessionsPath),
                                  app.getAppDir(),
                                  TenantApplications.create(componentRegistry, new MockReloadHandler(), TenantName.from("tenant")),
                                  new HostRegistry<>(),
                                  flagSource);
    }

    private FilesApplicationPackage getApplicationPackage(File testFile) throws IOException {
        File appDir = folder.newFolder();
        IOUtils.copyDirectory(testFile, appDir);
        return FilesApplicationPackage.fromFile(appDir);
    }

    private DeployHandlerLogger getLogger() {
        return new DeployHandlerLogger(new Slime().get(), false /*verbose */,
                                       new ApplicationId.Builder().tenant("testtenant").applicationName("testapp").build());
    }

    private ApplicationId applicationId(String applicationName) {
        return ApplicationId.from(TenantName.defaultName(),
                                  ApplicationName.from(applicationName), InstanceName.defaultName());
    }

    private static class FailWithTransientExceptionProvisioner implements Provisioner {

        @Override
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            throw new LoadBalancerServiceException("Unable to create load balancer", new Exception("some internal exception"));
        }

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) { }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) { }

        @Override
        public void restart(ApplicationId application, HostFilter filter) { }

    }

}
