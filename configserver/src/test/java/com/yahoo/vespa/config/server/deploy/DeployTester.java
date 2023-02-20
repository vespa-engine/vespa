// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.HostedConfigModelRegistry;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ConfigConvergenceChecker;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author bratseth
 */
public class DeployTester {

    private static final TenantName tenantName = TenantName.from("deploytester");
    private static final ApplicationId applicationId = ApplicationId.from(tenantName.value(), "myApp", "default");

    private final Clock clock;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    private DeployTester(Clock clock, TenantRepository tenantRepository, ApplicationRepository applicationRepository) {
        this.clock = clock;
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
    }

    public Tenant tenant() {
        return tenantRepository.getTenant(tenantName);
    }

    public Tenant tenant(ApplicationId applicationId) {
        return tenantRepository.getTenant(applicationId.tenant());
    }

    /** Create a model factory for the version of this source*/
    public static CountingModelFactory createModelFactory(Clock clock) {
        return new CountingModelFactory(clock);
    }

    /** Create a model factory for a particular version */
    public static CountingModelFactory createModelFactory(Version version) {
        return createModelFactory(version, Clock.systemUTC());
    }

    /** Create a model factory for a particular version and clock */
    public static CountingModelFactory createModelFactory(Version version, Clock clock) {
        return createModelFactory(version, clock, Zone.defaultZone());
    }

    /** Create a model factory for a particular version, clock and zone */
    public static CountingModelFactory createModelFactory(Version version, Clock clock, Zone zone) {
        return new CountingModelFactory(version, clock, zone);
    }

    /** Create a model factory which always fails validation */
    public static ModelFactory createFailingModelFactory(Version version) { return new FailingModelFactory(version); }


    public static CountingModelFactory createHostedModelFactory(Version version, Clock clock) {
        return new CountingModelFactory(HostedConfigModelRegistry.create(), version, clock, Zone.defaultZone());
    }

    public static CountingModelFactory createHostedModelFactory(Version version, Zone zone) {
        return new CountingModelFactory(HostedConfigModelRegistry.create(), version, Clock.systemUTC(), zone);
    }

    public static CountingModelFactory createHostedModelFactory(Version version) {
        return new CountingModelFactory(HostedConfigModelRegistry.create(), version, Clock.systemUTC(), Zone.defaultZone());
    }

    public static CountingModelFactory createHostedModelFactory(Clock clock) {
        return new CountingModelFactory(HostedConfigModelRegistry.create(), clock);
    }

    public static CountingModelFactory createHostedModelFactory() {
        return new CountingModelFactory(HostedConfigModelRegistry.create(), Clock.systemUTC());
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public void deployApp(String applicationPath) {
        deployApp(applicationPath, (String) null);
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public PrepareResult deployApp(String applicationPath, String vespaVersion)  {
        return deployApp(applicationPath, new PrepareParams.Builder().vespaVersion(vespaVersion));
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public PrepareResult deployApp(String applicationPath, PrepareParams.Builder paramsBuilder)  {
         paramsBuilder.applicationId(applicationId)
                .timeoutBudget(new TimeoutBudget(clock, Duration.ofSeconds(60)));

        return applicationRepository.deploy(new File(applicationPath), paramsBuilder.build());
    }

    public AllocatedHosts getAllocatedHostsOf(ApplicationId applicationId) {
        Optional<Session> session = applicationRepository.getActiveSession(tenant(applicationId), applicationId);
        return session.orElseThrow(() -> new IllegalArgumentException("No active session for " + applicationId)).getAllocatedHosts();
    }

    public ApplicationId applicationId() { return applicationId; }

    public Optional<com.yahoo.config.provision.Deployment> redeployFromLocalActive() {
        return applicationRepository.deployFromLocalActive(applicationId);
    }

    public Optional<com.yahoo.config.provision.Deployment> redeployFromLocalActive(ApplicationId id) {
        return applicationRepository.deployFromLocalActive(id, Duration.ofSeconds(60));
    }

    public ApplicationRepository applicationRepository() {
        return applicationRepository;
    }

    public Curator curator() {
        return tenantRepository.getCurator();
    }

    private static HostProvisioner createProvisioner() {
        return new InMemoryProvisioner(9, false);
    }

    private static class FailingModelFactory implements ModelFactory {

        private final Version version;

        public FailingModelFactory(Version version) {
            this.version = version;
        }

        @Override
        public Version version() { return version; }

        @Override
        public Model createModel(ModelContext modelContext) {
            try {
                Instant now = LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
                ApplicationPackage application = new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().build();
                DeployState deployState = new DeployState.Builder().applicationPackage(application).now(now).build();
                return new VespaModel(deployState);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            if ( ! validationParameters.ignoreValidationErrors())
                throw new IllegalArgumentException("Model building fails");
            return new ModelCreateResult(createModel(modelContext), Collections.emptyList());
        }

    }

    /** A wrapper of the regular model factory which counts the number of models it has created */
    public static class CountingModelFactory implements ModelFactory {

        private final VespaModelFactory wrapped;
        private int creationCount;

        public CountingModelFactory(Clock clock) {
            this.wrapped = VespaModelFactory.createTestFactory(new NullConfigModelRegistry(), clock);
        }

        public CountingModelFactory(Version version, Clock clock, Zone zone) {
            this.wrapped = VespaModelFactory.createTestFactory(version, new NullConfigModelRegistry(), clock, zone);
        }

        public CountingModelFactory(ConfigModelRegistry registry, Clock clock) {
            this.wrapped = VespaModelFactory.createTestFactory(registry, clock);
        }

        public CountingModelFactory(ConfigModelRegistry registry, Version version, Clock clock, Zone zone) {
            this.wrapped = VespaModelFactory.createTestFactory(version, registry, clock, zone);
        }

        /** Returns the number of models created successfully by this instance */
        public int creationCount() { return creationCount; }

        @Override
        public Version version() { return wrapped.version(); }

        @Override
        public Model createModel(ModelContext modelContext) {
            Model model = wrapped.createModel(modelContext);
            creationCount++;
            return model;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            ModelCreateResult result = wrapped.createAndValidateModel(modelContext, validationParameters);
            creationCount++;
            return result;
        }

    }

    public static class Builder {

        private final TemporaryFolder temporaryFolder;
        private Clock clock;
        private Provisioner provisioner;
        private ConfigserverConfig configserverConfig;
        private Zone zone;
        private Curator curator = new MockCurator();
        private Metrics metrics;
        private List<ModelFactory> modelFactories;
        private ConfigConvergenceChecker configConvergenceChecker = new ConfigConvergenceChecker();
        private FlagSource flagSource = new InMemoryFlagSource();

        public Builder(TemporaryFolder temporaryFolder) {
            this.temporaryFolder = temporaryFolder;
        }

        public DeployTester build() {
            Clock clock = Optional.ofNullable(this.clock).orElseGet(Clock::systemUTC);
            Zone zone = Optional.ofNullable(this.zone).orElseGet(Zone::defaultZone);
            ConfigserverConfig configserverConfig = Optional.ofNullable(this.configserverConfig)
                    .orElseGet(() -> new ConfigserverConfig(new ConfigserverConfig.Builder()
                            .configServerDBDir(uncheck(() -> Files.createTempDirectory("serverdb")).toString())
                            .configDefinitionsDir(uncheck(() -> Files.createTempDirectory("configdefinitions")).toString())
                            .fileReferencesDir(uncheck(() -> Files.createTempDirectory("configdefinitions")).toString())));
            Provisioner provisioner = Optional.ofNullable(this.provisioner)
                    .orElseGet(() -> new MockProvisioner().hostProvisioner(createProvisioner()));
            List<ModelFactory> modelFactories = Optional.ofNullable(this.modelFactories)
                    .orElseGet(() -> List.of(createModelFactory(clock)));

            TestTenantRepository.Builder builder = new TestTenantRepository.Builder()
                    .withClock(clock)
                    .withConfigserverConfig(configserverConfig)
                    .withCurator(curator)
                    .withFileDistributionFactory(new MockFileDistributionFactory(configserverConfig))
                    .withMetrics(Optional.ofNullable(metrics).orElse(Metrics.createTestMetrics()))
                    .withModelFactoryRegistry((new ModelFactoryRegistry(modelFactories)))
                    .withZone(zone)
                    .withFlagSource(flagSource);

            if (configserverConfig.hostedVespa()) builder.withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(provisioner, true));

            TenantRepository tenantRepository = builder.build();
            tenantRepository.addTenant(tenantName);

            ApplicationRepository applicationRepository = new ApplicationRepository.Builder()
                    .withTenantRepository(tenantRepository)
                    .withConfigserverConfig(configserverConfig)
                    .withOrchestrator(new OrchestratorMock())
                    .withClock(clock)
                    .withProvisioner(provisioner)
                    .withConfigConvergenceChecker(configConvergenceChecker)
                    .withFlagSource(flagSource)
                    .build();

            return new DeployTester(clock, tenantRepository, applicationRepository);
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder provisioner(Provisioner provisioner) {
            this.provisioner = provisioner;
            return this;
        }

        public Builder hostProvisioner(HostProvisioner hostProvisioner) {
            return provisioner(new MockProvisioner().hostProvisioner(hostProvisioner));
        }

        public Builder configserverConfig(ConfigserverConfig configserverConfig) {
            this.configserverConfig = configserverConfig;
            return this;
        }

        public Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public Builder curator(Curator curator) {
            this.curator = curator;
            return this;
        }

        public Builder metrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder modelFactory(ModelFactory modelFactory) {
            return modelFactories(List.of(modelFactory));
        }

        public Builder modelFactories(List<ModelFactory> modelFactories) {
            this.modelFactories = modelFactories;
            return this;
        }

        public Builder configConvergenceChecker(ConfigConvergenceChecker configConvergenceChecker) {
            this.configConvergenceChecker = configConvergenceChecker;
            return this;
        }

        public Builder flagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
            return this;
        }

        public Builder hostedConfigserverConfig(Zone zone) {
            try {
                this.configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                      .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                                                      .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                                                      .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                                                      .hostedVespa(true)
                                                      .multitenant(true)
                                                      .region(zone.region().value())
                                                      .environment(zone.environment().value())
                                                      .system(zone.system().value()));
                return this;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
