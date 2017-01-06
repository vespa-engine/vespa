// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Version;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.Tenants;
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

    private final Curator curator;
    private final Tenants tenants;
    private final File testApp;

    private ApplicationId id;

    public DeployTester(String appPath) {
        this(appPath, Collections.singletonList(createDefaultModelFactory(Clock.systemUTC())));
    }

    public DeployTester(String appPath, List<ModelFactory> modelFactories) {
        this(appPath, modelFactories, new ConfigserverConfig(new ConfigserverConfig.Builder()
                                                                     .configServerDBDir(Files.createTempDir()
                                                                                             .getAbsolutePath())));
    }

    public DeployTester(String appPath, ConfigserverConfig configserverConfig) {
        this(appPath,
             Collections.singletonList(createDefaultModelFactory(Clock.systemUTC())),
             configserverConfig);
    }

    public DeployTester(String appPath, List<ModelFactory> modelFactories, ConfigserverConfig configserverConfig) {
        Metrics metrics = Metrics.createTestMetrics();
        this.curator = new MockCurator();
        TestComponentRegistry componentRegistry = createComponentRegistry(curator, metrics, modelFactories, configserverConfig);
        try {
            this.testApp = new File(appPath);
            this.tenants = new Tenants(componentRegistry, metrics);
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Tenant tenant() { return tenants.defaultTenant(); }
    
    /** Create the model factory which will be used in production */
    public static ModelFactory createDefaultModelFactory(Clock clock) { return new VespaModelFactory(new NullConfigModelRegistry(), clock); }
    
    /** Create a model factory which always fails validation */
    public static ModelFactory createFailingModelFactory(Version version) { return new FailingModelFactory(version); }
    
    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public ApplicationId deployApp(String appName){
        return deployApp(appName, Optional.empty());
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    public ApplicationId deployApp(String appName, Optional<String> dockerVespaImageVersion)  {
        final Tenant tenant = tenant();
        LocalSession session = tenant.getSessionFactory().createSession(testApp, appName, new TimeoutBudget(Clock.systemUTC(), Duration.ofSeconds(60)));
        ApplicationId id = ApplicationId.from(tenant.getName(), ApplicationName.from(appName), InstanceName.defaultName());
        PrepareParams.Builder paramsBuilder = new PrepareParams.Builder()
                .applicationId(id);
        if (dockerVespaImageVersion.isPresent())
            paramsBuilder.dockerVespaImageVersion(dockerVespaImageVersion.get());
        session.prepare(new SilentDeployLogger(),
                        paramsBuilder.build(),
                        Optional.empty(),
                        tenant.getPath());
        session.createActivateTransaction().commit();
        tenant.getLocalSessionRepo().addSession(session);
        this.id = id;
        return id;
    }

    public ProvisionInfo getProvisionInfoFromDeployedApp(ApplicationId applicationId) {
        final Tenant tenant = tenant();
        LocalSession session = tenant.getLocalSessionRepo().getSession(tenant.getApplicationRepo()
                                                                             .getSessionIdForApplication(applicationId));
        return session.getProvisionInfo();
    }

    public ApplicationId applicationId() { return id; }

    public Optional<com.yahoo.config.provision.Deployment> redeployFromLocalActive() {
        return redeployFromLocalActive(id);
    }

    public Optional<com.yahoo.config.provision.Deployment> redeployFromLocalActive(ApplicationId id) {
        ApplicationRepository applicationRepository = new ApplicationRepository(tenants,
                                                                                HostProvisionerProvider.withProvisioner(createHostProvisioner()),
                                                                                curator,
                                                                                new LogServerLogGrabber(),
                                                                                new ApplicationConvergenceChecker());

        return applicationRepository.deployFromLocalActive(id, Duration.ofSeconds(60));
    }

    private Provisioner createHostProvisioner() {
        return new ProvisionerAdapter(new InMemoryProvisioner(true, "host0", "host1", "host2", "host3", "host4", "host5"));
    }

    private TestComponentRegistry createComponentRegistry(Curator curator, Metrics metrics,
                                                          List<ModelFactory> modelFactories,
                                                          ConfigserverConfig configserverConfig) {
        TestComponentRegistry.Builder builder = new TestComponentRegistry.Builder();

        if (configserverConfig.hostedVespa()) {
            builder.provisioner(createHostProvisioner());
        }

        builder.configServerConfig(configserverConfig)
               .curator(curator)
               .modelFactoryRegistry(new ModelFactoryRegistry(modelFactories))
               .metrics(metrics);
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
        public Version getVersion() { return version; }

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
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors) {
            if ( ! ignoreValidationErrors)
                throw new IllegalArgumentException("Validation fails");
            return new ModelCreateResult(createModel(modelContext), Collections.emptyList());
        }

    }

}
