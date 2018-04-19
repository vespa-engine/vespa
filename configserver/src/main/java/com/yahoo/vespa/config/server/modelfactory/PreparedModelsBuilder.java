// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

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
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.Version;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.provision.ProvisionerAdapter;
import com.yahoo.vespa.config.server.provision.StaticProvisioner;
import com.yahoo.vespa.config.server.session.FileDistributionFactory;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SessionContext;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
public class PreparedModelsBuilder extends ModelsBuilder<PreparedModelsBuilder.PreparedModelResult> {

    private static final Logger log = Logger.getLogger(PreparedModelsBuilder.class.getName());

    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final SessionContext context;
    private final DeployLogger logger;
    private final PrepareParams params;
    private final FileDistributionFactory fileDistributionFactory;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final Optional<ApplicationSet> currentActiveApplicationSet;
    private final ModelContext.Properties properties;

    public PreparedModelsBuilder(ModelFactoryRegistry modelFactoryRegistry,
                                 PermanentApplicationPackage permanentApplicationPackage,
                                 ConfigDefinitionRepo configDefinitionRepo,
                                 FileDistributionFactory fileDistributionFactory,
                                 HostProvisionerProvider hostProvisionerProvider,
                                 SessionContext context,
                                 DeployLogger logger,
                                 PrepareParams params,
                                 Optional<ApplicationSet> currentActiveApplicationSet,
                                 ModelContext.Properties properties) {
        super(modelFactoryRegistry, properties.hostedVespa(), properties.zone());
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configDefinitionRepo = configDefinitionRepo;

        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;

        this.context = context;
        this.logger = logger;
        this.params = params;
        this.currentActiveApplicationSet = currentActiveApplicationSet;

        this.properties = properties;
    }

    @Override
    protected PreparedModelResult buildModelVersion(ModelFactory modelFactory, 
                                                    ApplicationPackage applicationPackage,
                                                    ApplicationId applicationId, 
                                                    com.yahoo.component.Version wantedNodeVespaVersion,
                                                    Optional<AllocatedHosts> allocatedHosts,
                                                    Instant now) {
        Version modelVersion = modelFactory.getVersion();
        log.log(LogLevel.DEBUG, "Building model " + modelVersion + " for " + applicationId);
        FileDistributionProvider fileDistributionProvider = fileDistributionFactory.createProvider(context.getServerDBSessionDir());

        // Use empty on non-hosted systems, use already allocated hosts if available, create connection to a host provisioner otherwise
        Optional<HostProvisioner> hostProvisioner = createHostProvisioner(allocatedHosts);        
        Optional<Model> previousModel = currentActiveApplicationSet
                .map(set -> set.getForVersionOrLatest(Optional.of(modelVersion), now).getModel());
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
                new com.yahoo.component.Version(modelVersion.toString()),
                wantedNodeVespaVersion);

        log.log(LogLevel.DEBUG, "Create and validate model " + modelVersion + " for " + applicationId);
        ModelCreateResult result =  modelFactory.createAndValidateModel(modelContext, params.ignoreValidationErrors());
        validateModelHosts(context.getHostValidator(), applicationId, result.getModel());
        log.log(LogLevel.DEBUG, "Done building model " + modelVersion + " for " + applicationId);
        return new PreparedModelsBuilder.PreparedModelResult(modelVersion, result.getModel(), fileDistributionProvider, result.getConfigChangeActions());
    }

    // This method is an excellent demonstration of what happens when one is too liberal with Optional   
    // -bratseth, who had to write the below  :-\
    private Optional<HostProvisioner> createHostProvisioner(Optional<AllocatedHosts> allocatedHosts) {
        Optional<HostProvisioner> nodeRepositoryProvisioner = createNodeRepositoryProvisioner(properties);
        if ( ! allocatedHosts.isPresent()) return nodeRepositoryProvisioner;
        
        Optional<HostProvisioner> staticProvisioner = createStaticProvisioner(allocatedHosts);
        if ( ! staticProvisioner.isPresent()) return Optional.empty(); // Since we have hosts allocated this means we are on non-hosted

        // The following option should not be possible, but since there is a right action for it we can take it
        if ( ! nodeRepositoryProvisioner.isPresent())
            return Optional.of(new StaticProvisioner(allocatedHosts.get()));
            
        // Nodes are already allocated by a model and we should use them unless this model requests hosts from a
        // previously unallocated cluster. This allows future models to stop allocate certain clusters.
        return Optional.of(new StaticProvisioner(allocatedHosts.get(), nodeRepositoryProvisioner.get()));
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

    private Optional<HostProvisioner> createNodeRepositoryProvisioner(ModelContext.Properties properties) {
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
