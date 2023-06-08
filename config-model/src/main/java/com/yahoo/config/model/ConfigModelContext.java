// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.Bcp.Group;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.ClusterInfo.Builder;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.model.VespaModel;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This class contains a context that is passed to a model builder, and can be used to retrieve the application package,
 * logger etc.
 *
 * @author Ulf Lilleengen
 */
public final class ConfigModelContext {

    private final TreeConfigProducer<AnyConfigProducer> parent;
    private final String producerId;
    private final DeployState deployState;
    private final VespaModel vespaModel;
    private final ConfigModelRepoAdder configModelRepoAdder;
    private final ApplicationType applicationType;

    private ConfigModelContext(ApplicationType applicationType,
                               DeployState deployState,
                               VespaModel vespaModel,
                               ConfigModelRepoAdder configModelRepoAdder,
                               TreeConfigProducer<AnyConfigProducer> parent,
                               String producerId) {
        this.applicationType = applicationType;
        this.deployState = deployState;
        this.vespaModel = vespaModel;
        this.configModelRepoAdder = configModelRepoAdder;
        this.parent = parent;
        this.producerId = producerId;
    }

    public ApplicationPackage getApplicationPackage() { return deployState.getApplicationPackage(); }
    public String getProducerId() { return producerId; }
    public TreeConfigProducer<AnyConfigProducer> getParentProducer() { return parent; }
    public DeployLogger getDeployLogger() { return deployState.getDeployLogger(); }
    public DeployState getDeployState() { return deployState; }
    public ApplicationType getApplicationType() { return applicationType; }
    public VespaModel vespaModel() { return vespaModel; }
    public ModelContext.Properties properties() { return deployState.getProperties(); }
    public ModelContext.FeatureFlags featureFlags() { return deployState.featureFlags(); }

    /** Returns write access to the config model repo, or null (only) if this is improperly initialized during testing */
    public ConfigModelRepoAdder getConfigModelRepoAdder() { return configModelRepoAdder; }

    /** Create a new context with a different parent */
    public ConfigModelContext withParent(TreeConfigProducer<AnyConfigProducer> newParent) {
        return ConfigModelContext.create(deployState, vespaModel, configModelRepoAdder, newParent, producerId);
    }

    /** Create a new context with a different config model producer id */
    public ConfigModelContext withId(String producerId) {
        return ConfigModelContext.create(deployState, vespaModel, configModelRepoAdder, parent, producerId);
    }

    public ConfigModelContext with(VespaModel vespaModel) {
        return ConfigModelContext.create(deployState, vespaModel, configModelRepoAdder, parent, producerId);
    }

    /** Returns a cluster info builder pre-populated with info known in this context. */
    public ClusterInfo.Builder clusterInfo() {
        DeploymentSpec spec = getApplicationPackage().getDeploymentSpec();
        ClusterInfo.Builder builder = new ClusterInfo.Builder();
        spec.hostTTL(properties().applicationId().instance(), deployState.zone().environment(), deployState.zone().region())
            .ifPresent(ttl -> {
                ZoneId zoneId = ZoneId.from(deployState.zone().environment(), deployState.zone().region());
                if (spec.cloudAccount(deployState.zone().cloud().name(), properties().applicationId().instance(), zoneId).isUnspecified())
                    throw new IllegalArgumentException("deployment spec specifies host TTL for " + zoneId +
                                                       " but no cloud account is specified for this zone");
            });
        spec.instance(properties().applicationId().instance())
            .flatMap(instance -> instance.bcp().groups().stream()
                                         .filter(group -> group.memberRegions().contains(properties().zone().region()))
                                         .map(Group::deadline)
                                         .min(Comparator.naturalOrder()))
            .ifPresent(builder::bcpDeadline);
        return builder;
    }

    /**
     * Create an application context from a parent producer and an id.
     *
     * @param deployState the global deploy state for this model
     * @param parent the parent to be used for the config model
     * @param producerId the id to be used for the config model
     * @return a model context that can be passed to a model
     */
    public static ConfigModelContext create(DeployState deployState,
                                            VespaModel vespaModel,
                                            ConfigModelRepoAdder configModelRepoAdder,
                                            TreeConfigProducer<AnyConfigProducer> parent,
                                            String producerId) {
        return new ConfigModelContext(ApplicationType.DEFAULT, deployState, vespaModel, configModelRepoAdder, parent, producerId);
    }

    /**
     * Create an application context from an application type, a parent producer and an id.
     *
     * @param applicationType the application type
     * @param deployState the global deploy state for this model
     * @param parent the parent to be used for the config model
     * @param producerId the id to be used for the config model
     * @return a model context that can be passed to a model
     */
    public static ConfigModelContext create(ApplicationType applicationType,
                                            DeployState deployState,
                                            VespaModel vespaModel,
                                            ConfigModelRepoAdder configModelRepoAdder,
                                            TreeConfigProducer<AnyConfigProducer> parent,
                                            String producerId) {
        return new ConfigModelContext(applicationType, deployState, vespaModel, configModelRepoAdder, parent, producerId);
    }

    public enum ApplicationType {
        DEFAULT("default"),
        HOSTED_INFRASTRUCTURE("hosted-infrastructure");

        private final String type;

        ApplicationType(String type) {
            this.type = type;
        }

        public static ApplicationType fromString(String value) {
            return Stream.of(ApplicationType.values())
                    .filter(applicationType -> applicationType.type.equals(value))
                    .findFirst()
                    .orElse(DEFAULT);

        }

    }
}
