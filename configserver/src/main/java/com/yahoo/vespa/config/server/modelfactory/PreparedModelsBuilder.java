// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ApplicationSet;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.HostValidator;
import com.yahoo.vespa.config.server.Rotations;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.provision.ProvisionerAdapter;
import com.yahoo.vespa.config.server.session.FileDistributionFactory;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SessionContext;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class PreparedModelsBuilder extends ModelsBuilder<PreparedModelsBuilder.PreparedModelResult> {

    private static final Logger log = Logger.getLogger(PreparedModelsBuilder.class.getName());

    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Curator curator;
    private final Zone zone;
    private final SessionContext context;
    private final DeployLogger logger;
    private final PrepareParams params;
    private final FileDistributionFactory fileDistributionFactory;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final Optional<ApplicationSet> currentActiveApplicationSet;
    private final ApplicationId applicationId;
    private final Rotations rotations;
    private final Set<Rotation> rotationsSet;
    private final ModelContext.Properties properties;

    /** Construct from global component registry */
    public PreparedModelsBuilder(GlobalComponentRegistry globalComponentRegistry,
                                 FileDistributionFactory fileDistributionFactory,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 SessionContext context,
                                 DeployLogger logger,
                                 PrepareParams params,
                                 Optional<ApplicationSet> currentActiveApplicationSet,
                                 Path tenantPath) {
        super(globalComponentRegistry.getModelFactoryRegistry());
        this.permanentApplicationPackage = globalComponentRegistry.getPermanentApplicationPackage();
        this.configserverConfig = globalComponentRegistry.getConfigserverConfig();
        this.configDefinitionRepo = globalComponentRegistry.getConfigDefinitionRepo();
        this.curator = globalComponentRegistry.getCurator();
        this.zone = globalComponentRegistry.getZone();

        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;

        this.context = context;
        this.logger = logger;
        this.params = params;
        this.currentActiveApplicationSet = currentActiveApplicationSet;

        this.applicationId = params.getApplicationId();
        this.rotations = new Rotations(curator, tenantPath);
        this.rotationsSet = getRotations(params.rotations());
        this.properties = createModelContextProperties(
                params.getApplicationId(),
                configserverConfig,
                zone,
                rotationsSet);
    }

    /** Construct with all dependencies passed separately */
    public PreparedModelsBuilder(ModelFactoryRegistry modelFactoryRegistry,
                                 PermanentApplicationPackage permanentApplicationPackage,
                                 ConfigserverConfig configserverConfig,
                                 ConfigDefinitionRepo configDefinitionRepo,
                                 Curator curator,
                                 Zone zone,
                                 FileDistributionFactory fileDistributionFactory,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 SessionContext context,
                                 DeployLogger logger,
                                 PrepareParams params,
                                 Optional<ApplicationSet> currentActiveApplicationSet,
                                 Path tenantPath) {
        super(modelFactoryRegistry);
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configserverConfig = configserverConfig;
        this.configDefinitionRepo = configDefinitionRepo;
        this.curator = curator;
        this.zone = zone;

        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;

        this.context = context;
        this.logger = logger;
        this.params = params;
        this.currentActiveApplicationSet = currentActiveApplicationSet;

        this.applicationId = params.getApplicationId();
        this.rotations = new Rotations(curator, tenantPath);
        this.rotationsSet = getRotations(params.rotations());
        this.properties = new ModelContextImpl.Properties(
                params.getApplicationId(),
                configserverConfig.multitenant(),
                ConfigServerSpec.fromConfig(configserverConfig),
                configserverConfig.hostedVespa(),
                zone,
                rotationsSet);
    }

    @Override
    protected PreparedModelResult buildModelVersion(ModelFactory modelFactory, ApplicationPackage applicationPackage,
                                                    ApplicationId applicationId) {
        Version version = modelFactory.getVersion();
        log.log(LogLevel.DEBUG, "Start building model for Vespa version " + version);
        FileDistributionProvider fileDistributionProvider = fileDistributionFactory.createProvider(
                context.getServerDBSessionDir(),
                applicationId);

        Optional<HostProvisioner> hostProvisioner = createHostProvisionerAdapter(properties);
        Optional<Model> previousModel = currentActiveApplicationSet
                .map(set -> set.getForVersionOrLatest(Optional.of(version)).getModel());
        ModelContext modelContext = new ModelContextImpl(
                applicationPackage,
                previousModel,
                permanentApplicationPackage.applicationPackage(),
                logger,
                configDefinitionRepo,
                fileDistributionProvider.getFileRegistry(),
                hostProvisioner,
                properties,
                getAppDir(applicationPackage),
                Optional.of(version));

        log.log(LogLevel.DEBUG, "Running createAndValidateModel for Vespa version " + version);
        ModelCreateResult result = modelFactory.createAndValidateModel(modelContext, params.ignoreValidationErrors());
        validateModelHosts(context.getHostValidator(), applicationId, result.getModel());
        log.log(LogLevel.DEBUG, "Done building model for Vespa version " + version);
        return new PreparedModelsBuilder.PreparedModelResult(version, result.getModel(), fileDistributionProvider, result.getConfigChangeActions());
    }

    private Optional<File> getAppDir(ApplicationPackage applicationPackage) {
        try {
            return applicationPackage instanceof FilesApplicationPackage ?
                   Optional.of(((FilesApplicationPackage) applicationPackage).getAppDir()) :
                   Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException("Could not find app dir", e);
        }
    }

    private void validateModelHosts(HostValidator<ApplicationId> hostValidator, ApplicationId applicationId, Model model) {
        hostValidator.verifyHosts(applicationId, model.getHosts().stream().map(hostInfo -> hostInfo.getHostname())
                .collect(Collectors.toList()));
    }

    private Set<Rotation> getRotations(Set<Rotation> rotations) {
        if (rotations == null || rotations.isEmpty()) {
            rotations = this.rotations.readRotationsFromZooKeeper(applicationId);
        }
        return rotations;
    }

    private Optional<HostProvisioner> createHostProvisionerAdapter(ModelContext.Properties properties) {
        return hostProvisionerProvider.getHostProvisioner().map(
                provisioner -> new ProvisionerAdapter(provisioner, properties.applicationId()));
    }


    /** The result of preparing a single model version */
    public static class PreparedModelResult implements ModelResult {

        public final Version version;
        public final Model model;
        public final FileDistributionProvider fileDistributionProvider;
        public final List<ConfigChangeAction> actions;

        public PreparedModelResult(Version version, Model model,
                                   FileDistributionProvider fileDistributionProvider, List<ConfigChangeAction> actions) {
            this.version = version;
            this.model = model;
            this.fileDistributionProvider = fileDistributionProvider;
            this.actions = actions;
        }

        @Override
        public Model getModel() {
            return model;
        }

    }

}
