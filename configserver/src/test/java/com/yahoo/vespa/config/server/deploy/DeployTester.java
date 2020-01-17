// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
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
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockTesterClient;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.LogRetriever;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.VespaModelFactory;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class DeployTester {

    private static final TenantName tenantName = TenantName.from("deploytester");
    private static final ApplicationId applicationId = ApplicationId.from(tenantName.value(), "myApp", "default");

    private final Clock clock;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    public DeployTester() {
        this(Collections.singletonList(createModelFactory(Clock.systemUTC())));
    }

    public DeployTester(List<ModelFactory> modelFactories) {
        this(modelFactories,
             new ConfigserverConfig(new ConfigserverConfig.Builder()
                     .configServerDBDir(Files.createTempDir().getAbsolutePath())
                     .configDefinitionsDir(Files.createTempDir().getAbsolutePath())),
             Clock.systemUTC());
    }

    public DeployTester(ConfigserverConfig configserverConfig) {
        this(Collections.singletonList(createModelFactory(Clock.systemUTC())), configserverConfig, Clock.systemUTC());
    }

    public DeployTester(ConfigserverConfig configserverConfig, HostProvisioner provisioner) {
        this(Collections.singletonList(createModelFactory(Clock.systemUTC())), configserverConfig, Clock.systemUTC(), provisioner);
    }

    public DeployTester(ConfigserverConfig configserverConfig, Clock clock) {
        this(Collections.singletonList(createModelFactory(clock)), configserverConfig, clock);
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig) {
        this(modelFactories, configserverConfig, Clock.systemUTC());
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, Clock clock) {
        this(modelFactories, configserverConfig, clock, Zone.defaultZone());
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, HostProvisioner hostProvisioner) {
        this(modelFactories, configserverConfig, Clock.systemUTC(), hostProvisioner);
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, Clock clock, HostProvisioner provisioner) {
        this(modelFactories, configserverConfig, clock, Zone.defaultZone(), provisioner);
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, Clock clock, Zone zone) {
        this(modelFactories, configserverConfig, clock, zone, createProvisioner());
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, Clock clock, Zone zone, HostProvisioner provisioner) {
        this(modelFactories, configserverConfig, clock, zone, provisioner, new MockCurator());
    }

    public DeployTester(List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig, Clock clock, Zone zone,
                        HostProvisioner provisioner, Curator curator) {
        this.clock = clock;
        TestComponentRegistry componentRegistry = createComponentRegistry(curator, Metrics.createTestMetrics(),
                                                                          modelFactories, configserverConfig, clock, zone,
                                                                          provisioner);
        try {
            this.tenantRepository = new TenantRepository(componentRegistry);
            tenantRepository.addTenant(tenantName);
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        applicationRepository = new ApplicationRepository(tenantRepository,
                                                          new ProvisionerAdapter(provisioner),
                                                          new OrchestratorMock(),
                                                          configserverConfig,
                                                          new LogRetriever(),
                                                          clock,
                                                          new MockTesterClient());
    }

    public Tenant tenant() {
        return tenantRepository.getTenant(tenantName);
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

    /** Create a model factory for a particular version and zone */
    public static CountingModelFactory createModelFactory(Version version, Zone zone) {
        return new CountingModelFactory(version, Clock.systemUTC(), zone);
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
    public PrepareResult deployApp(String applicationPath) {
        return deployApp(applicationPath, null, Instant.now());
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public PrepareResult deployApp(String applicationPath, Instant now) {
        return deployApp(applicationPath, null, now);
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public PrepareResult deployApp(String applicationPath, String vespaVersion) {
        return deployApp(applicationPath, vespaVersion, Instant.now());
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public PrepareResult deployApp(String applicationPath, String vespaVersion, Instant now)  {
        PrepareParams.Builder paramsBuilder = new PrepareParams.Builder()
                .applicationId(applicationId)
                .timeoutBudget(new TimeoutBudget(clock, Duration.ofSeconds(60)));
        if (vespaVersion != null)
            paramsBuilder.vespaVersion(vespaVersion);

        return applicationRepository.deploy(new File(applicationPath), paramsBuilder.build(), false, now);
    }

    public AllocatedHosts getAllocatedHostsOf(ApplicationId applicationId) {
        Tenant tenant = tenant();
        LocalSession session = tenant.getLocalSessionRepo().getSession(tenant.getApplicationRepo()
                                                                             .requireActiveSessionOf(applicationId));
        return session.getAllocatedHosts();
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

    private static HostProvisioner createProvisioner() {
        return new InMemoryProvisioner(true, "host0", "host1", "host2", "host3", "host4", "host5");
    }

    private TestComponentRegistry createComponentRegistry(Curator curator, Metrics metrics,
                                                          List<ModelFactory> modelFactories,
                                                          ConfigserverConfig configserverConfig,
                                                          Clock clock,
                                                          Zone zone,
                                                          HostProvisioner provisioner) {
        TestComponentRegistry.Builder builder = new TestComponentRegistry.Builder();

        if (configserverConfig.hostedVespa())
            builder.provisioner(new ProvisionerAdapter(provisioner));

        builder.configServerConfig(configserverConfig)
               .curator(curator)
               .modelFactoryRegistry(new ModelFactoryRegistry(modelFactories))
               .metrics(metrics)
               .zone(zone)
               .clock(clock);
        return builder.build();
    }

    private static class ProvisionerAdapter implements Provisioner {

        private final HostProvisioner hostProvisioner;

        public ProvisionerAdapter(HostProvisioner hostProvisioner) {
            this.hostProvisioner = hostProvisioner;
        }

        @Override
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) {
            return hostProvisioner.prepare(cluster, capacity, groups, logger);
        }

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
            // noop
        }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) {
            // noop
        }

        @Override
        public void restart(ApplicationId application, HostFilter filter) {
            // noop
        }

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
                throw new IllegalArgumentException("Validation fails");
            return new ModelCreateResult(createModel(modelContext), Collections.emptyList());
        }

    }

    /** A wrapper of the regular model factory which counts the number of models it has created */
    public static class CountingModelFactory implements ModelFactory {

        private final VespaModelFactory wrapped;
        private int creationCount;

        public CountingModelFactory(Clock clock) {
            this.wrapped = new VespaModelFactory(new NullConfigModelRegistry(), clock);
        }

        public CountingModelFactory(Version version, Clock clock, Zone zone) {
            this.wrapped = new VespaModelFactory(version, new NullConfigModelRegistry(), clock, zone);
        }

        public CountingModelFactory(ConfigModelRegistry registry, Clock clock) {
            this.wrapped = new VespaModelFactory(registry, clock);
        }

        public CountingModelFactory(ConfigModelRegistry registry, Version version, Clock clock, Zone zone) {
            this.wrapped = new VespaModelFactory(version, registry, clock, zone);
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

}
