// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.collect.ImmutableList;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Vtag;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.SettableOptional;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.deploy.ModelContextImpl;
import com.yahoo.vespa.config.server.deploy.ZooKeeperDeployer;
import com.yahoo.vespa.config.server.http.InvalidApplicationException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.modelfactory.PreparedModelsBuilder;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.Rotations;
import com.yahoo.vespa.curator.Curator;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A SessionPreparer is responsible for preparing a session given an application package.
 *
 * @author Ulf Lilleengen
 */
public class SessionPreparer {

    private static final Logger log = Logger.getLogger(SessionPreparer.class.getName());

    private final ModelFactoryRegistry modelFactoryRegistry;
    private final FileDistributionFactory fileDistributionFactory;
    private final HostProvisionerProvider hostProvisionerProvider;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo configDefinitionRepo;
    private final Curator curator;
    private final Zone zone;

    public SessionPreparer(ModelFactoryRegistry modelFactoryRegistry,
                           FileDistributionFactory fileDistributionFactory,
                           HostProvisionerProvider hostProvisionerProvider,
                           PermanentApplicationPackage permanentApplicationPackage,
                           ConfigserverConfig configserverConfig,
                           ConfigDefinitionRepo configDefinitionRepo,
                           Curator curator,
                           Zone zone) {
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.fileDistributionFactory = fileDistributionFactory;
        this.hostProvisionerProvider = hostProvisionerProvider;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.configserverConfig = configserverConfig;
        this.configDefinitionRepo = configDefinitionRepo;
        this.curator = curator;
        this.zone = zone;
    }

    /**
     * Prepares a session (validates, builds model, writes to zookeeper and distributes files)
     *
     * @param context                     Contains classes needed to read/write session data.
     * @param logger                      For storing logs returned in response to client.
     * @param params                      parameters controlling behaviour of prepare.
     * @param currentActiveApplicationSet Set of currently active applications.
     * @param tenantPath Zookeeper path for the tenant for this session
     * @return the config change actions that must be done to handle the activation of the models prepared.
     */
    public ConfigChangeActions prepare(SessionContext context, DeployLogger logger, PrepareParams params,
                                       Optional<ApplicationSet> currentActiveApplicationSet, Path tenantPath, 
                                       Instant now) {
        Preparation preparation = new Preparation(context, logger, params, currentActiveApplicationSet, tenantPath);
        preparation.preprocess();
        try {
            AllocatedHosts allocatedHosts = preparation.buildModels(now);
            preparation.makeResult(allocatedHosts);
            if ( ! params.isDryRun()) {
                preparation.writeStateZK();
                preparation.writeRotZK();
                preparation.distribute();
            }
            log.log(LogLevel.DEBUG, () -> "time used " + params.getTimeoutBudget().timesUsed() +
                    " : " + params.getApplicationId());
            return preparation.result();
        } catch (OutOfCapacityException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InvalidApplicationException("Invalid application package", e);
        }
    }

    private class Preparation {

        final SessionContext context;
        final DeployLogger logger;
        final PrepareParams params;

        final Optional<ApplicationSet> currentActiveApplicationSet;
        final Path tenantPath;
        final ApplicationId applicationId;

        /** The version of Vespa the application to be prepared specifies for its nodes */
        final com.yahoo.component.Version vespaVersion;

        final Rotations rotations;
        final Set<Rotation> rotationsSet;
        final ModelContext.Properties properties;

        private ApplicationPackage applicationPackage;
        private List<PreparedModelsBuilder.PreparedModelResult> modelResultList;
        private PrepareResult prepareResult;

        private final PreparedModelsBuilder preparedModelsBuilder;

        Preparation(SessionContext context, DeployLogger logger, PrepareParams params,
                    Optional<ApplicationSet> currentActiveApplicationSet, Path tenantPath) {
            this.context = context;
            this.logger = logger;
            this.params = params;
            this.currentActiveApplicationSet = currentActiveApplicationSet;
            this.tenantPath = tenantPath;

            this.applicationId = params.getApplicationId();
            this.vespaVersion = params.vespaVersion().orElse(Vtag.currentVersion);
            this.rotations = new Rotations(curator, tenantPath);
            this.rotationsSet = getRotations(params.rotations());
            this.properties = new ModelContextImpl.Properties(params.getApplicationId(),
                                                              configserverConfig.multitenant(),
                                                              ConfigServerSpec.fromConfig(configserverConfig),
                                                              HostName.from(configserverConfig.loadBalancerAddress()),
                                                              configserverConfig.ztsUrl() != null ? URI.create(configserverConfig.ztsUrl()) : null,
                                                              configserverConfig.athenzDnsSuffix(),
                                                              configserverConfig.hostedVespa(),
                                                              zone,
                                                              rotationsSet);
            this.preparedModelsBuilder = new PreparedModelsBuilder(modelFactoryRegistry,
                                                                   permanentApplicationPackage,
                                                                   configDefinitionRepo,
                                                                   fileDistributionFactory,
                                                                   hostProvisionerProvider,
                                                                   context,
                                                                   logger,
                                                                   params,
                                                                   currentActiveApplicationSet,
                                                                   properties);
        }

        void checkTimeout(String step) {
            if (! params.getTimeoutBudget().hasTimeLeft()) {
                String used = params.getTimeoutBudget().timesUsed();
                throw new RuntimeException("prepare timed out "+used+" after "+step+" step: " + applicationId);
            }
        }

        void preprocess() {
            try {
                this.applicationPackage = context.getApplicationPackage().preprocess(properties.zone(), logger);
            } catch (IOException | TransformerException | ParserConfigurationException | SAXException e) {
                throw new RuntimeException("Error preprocessing application package for " + applicationId, e);
            }
            checkTimeout("preprocess");
        }

        AllocatedHosts buildModels(Instant now) {
            SettableOptional<AllocatedHosts> allocatedHosts = new SettableOptional<>();
            this.modelResultList = preparedModelsBuilder.buildModels(applicationId, vespaVersion, 
                                                                     applicationPackage, allocatedHosts, now);
            checkTimeout("build models");
            return allocatedHosts.get();
        }

        void makeResult(AllocatedHosts allocatedHosts) {
            this.prepareResult = new PrepareResult(allocatedHosts, modelResultList);
            checkTimeout("making result from models");
        }

        void writeStateZK() {
            log.log(LogLevel.DEBUG, "Writing application package state to zookeeper");
            writeStateToZooKeeper(context.getSessionZooKeeperClient(), 
                                  applicationPackage,
                                  applicationId,
                                  vespaVersion,
                                  logger,
                                  prepareResult.getFileRegistries(), 
                                  prepareResult.allocatedHosts());
            checkTimeout("write state to zookeeper");
        }

        void writeRotZK() {
            rotations.writeRotationsToZooKeeper(applicationId, rotationsSet);
            checkTimeout("write rotations to zookeeper");
        }

        void distribute() {
            prepareResult.asList().forEach(modelResult -> modelResult.model
                                           .distributeFiles(modelResult.fileDistributionProvider.getFileDistribution()));
            checkTimeout("distribute files");
        }

        ConfigChangeActions result() {
            return prepareResult.getConfigChangeActions();
        }

        private Set<Rotation> getRotations(Set<Rotation> rotations) {
            if (rotations == null || rotations.isEmpty()) {
                rotations = this.rotations.readRotationsFromZooKeeper(applicationId);
            }
            return rotations;
        }

    }

    private void writeStateToZooKeeper(SessionZooKeeperClient zooKeeperClient,
                                       ApplicationPackage applicationPackage,
                                       ApplicationId applicationId,
                                       com.yahoo.component.Version vespaVersion,
                                       DeployLogger deployLogger,
                                       Map<Version, FileRegistry> fileRegistryMap,
                                       AllocatedHosts allocatedHosts) {
        ZooKeeperDeployer zkDeployer = zooKeeperClient.createDeployer(deployLogger);
        try {
            zkDeployer.deploy(applicationPackage, fileRegistryMap, allocatedHosts);
            zooKeeperClient.writeApplicationId(applicationId);
            zooKeeperClient.writeVespaVersion(vespaVersion);
        } catch (RuntimeException | IOException e) {
            zkDeployer.cleanup();
            throw new RuntimeException("Error preparing session", e);
        }
    }

    /** The result of preparation over all model versions */
    private static class PrepareResult {

        private final AllocatedHosts allocatedHosts;
        private final ImmutableList<PreparedModelsBuilder.PreparedModelResult> results;
        
        public PrepareResult(AllocatedHosts allocatedHosts, List<PreparedModelsBuilder.PreparedModelResult> results) {
            this.allocatedHosts = allocatedHosts;
            this.results = ImmutableList.copyOf(results);
        }

        /** Returns the results for each model as an immutable list */
        public List<PreparedModelsBuilder.PreparedModelResult> asList() { return results; }

        /** Returns the host allocations resulting from this preparation. */
        public AllocatedHosts allocatedHosts() { return allocatedHosts; }

        public Map<Version, FileRegistry> getFileRegistries() {
            return results.stream()
                    .collect(Collectors.toMap((prepareResult -> prepareResult.version),
                            (prepareResult -> prepareResult.fileDistributionProvider.getFileRegistry())));
        }

        /**
         * Collects the config change actions from all model factory creations and returns the aggregated union of these actions.
         * A system in the process of upgrading Vespa will have hosts running both version X and Y, and this will change
         * during the upgrade process. Trying to be smart about which actions to perform on which hosts depending
         * on the version running will be a nightmare to maintain. A pragmatic approach is therefore to just use the
         * union of all actions as this will give the correct end result at the cost of perhaps restarting nodes twice
         * (once for the upgrading case and once for a potential restart action).
         */
         public ConfigChangeActions getConfigChangeActions() {
            return new ConfigChangeActions(results.stream().
                map(result -> result.actions).
                flatMap(actions -> actions.stream()).
                collect(Collectors.toList()));
         }

    }

    /**
     * During model building each model version will request nodes allocated (from the node allocator)
     * for each cluster specified by that model. As allocations are stable this should usually
     * result in the same allocations for the same clusters across all model versions,
     * otherwise we should fail this preparation as such inconsistencies lead to undefined behavior,
     * and there is really just one true allocation (for a given cluster) to be activated in the node repository.
     * 
     * However, these disagreements between allocations in each model version are allowed:
     * - A node may be retired in some model version but not another. This allows model versions to change cluster sizes,
     *   and is ok because the system will converge on the latest version's opinion
     * - Clusters may be present on some version but not on another. This does not lead to inconsistency
     *   and allows new model versions to introduce new clusters.
     *   
     * For each cluster, the newest model version which has that cluster decides the correct retirement status of nodes
     * (and all model versions having the cluster must have the same nodes).
     * 
     * This class ensures these constraints and returns a reconciliated set of nodes which should be activated,
     * given a set of model activation results.
     */
    private static final class ReconciliatedHostAllocations {
        
        public ReconciliatedHostAllocations(List<PreparedModelsBuilder.PreparedModelResult> results) {
            
        }

    }
    
}
