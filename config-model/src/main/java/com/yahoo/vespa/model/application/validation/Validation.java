// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.application.api.ValidationOverrides.ValidationException;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.change.CertificateRemovalChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ConfigValueChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ContainerRestartValidator;
import com.yahoo.vespa.model.application.validation.change.ContentClusterRemovalValidator;
import com.yahoo.vespa.model.application.validation.change.ContentTypeRemovalValidator;
import com.yahoo.vespa.model.application.validation.change.GlobalDocumentChangeValidator;
import com.yahoo.vespa.model.application.validation.change.IndexedSearchClusterChangeValidator;
import com.yahoo.vespa.model.application.validation.change.IndexingModeChangeValidator;
import com.yahoo.vespa.model.application.validation.change.NodeResourceChangeValidator;
import com.yahoo.vespa.model.application.validation.change.RedundancyIncreaseValidator;
import com.yahoo.vespa.model.application.validation.change.ResourcesReductionValidator;
import com.yahoo.vespa.model.application.validation.change.RestartOnDeployForOnnxModelChangesValidator;
import com.yahoo.vespa.model.application.validation.change.StartupCommandChangeValidator;
import com.yahoo.vespa.model.application.validation.change.StreamingSearchClusterChangeValidator;
import com.yahoo.vespa.model.application.validation.first.RedundancyValidator;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Executor of validators. This defines the right order of validator execution.
 *
 * @author hmusum
 */
public class Validation {

    private final List<Validator> additionalValidators;

    public Validation() { this(List.of()); }

    /** Create instance taking additional validators (e.g., for cloud applications) */
    public Validation(List<Validator> additionalValidators) { this.additionalValidators = additionalValidators; }

    /**
     * Validates the model supplied, and if there already exists a model for the application validates changes
     * between the previous and current model
     *
     * @return a list of required changes needed to make this configuration live
     * @throws ValidationOverrides.ValidationException if the change fails validation
     */
    public List<ConfigChangeAction> validate(VespaModel model, ValidationParameters validationParameters, DeployState deployState) {
        Execution execution = new Execution(model, deployState);
        if (validationParameters.checkRouting()) {
            validateRouting(execution);
        }

        validateModel(validationParameters, execution);

        additionalValidators.forEach(execution::run);

        if (deployState.getProperties().isFirstTimeDeployment()) {
            validateFirstTimeDeployment(execution);
        }
        else if (deployState.getPreviousModel().isPresent() && (deployState.getPreviousModel().get() instanceof VespaModel vespaModel)) {
            validateChanges(vespaModel, execution);
                // TODO: Why is this done here? It won't be done on more than one config server?
            deferConfigChangesForClustersToBeRestarted(execution.actions, model);
        }

        execution.throwIfFailed();
        return execution.actions;
    }

    private static void validateRouting(Execution execution) {
        execution.run(new RoutingValidator());
        execution.run(new RoutingSelectorValidator());
    }

    private static void validateModel(ValidationParameters validationParameters, Execution execution) {
        execution.run(new SchemasDirValidator());
        execution.run(new BundleValidator());
        execution.run(new PublicApiBundleValidator());
        execution.run(new SearchDataTypeValidator());
        execution.run(new ComplexFieldsWithStructFieldAttributesValidator());
        execution.run(new ComplexFieldsWithStructFieldIndexesValidator());
        execution.run(new StreamingValidator());
        execution.run(new RankSetupValidator(validationParameters.ignoreValidationErrors()));
        execution.run(new NoPrefixForIndexes());
        execution.run(new ContainerInCloudValidator());
        execution.run(new DeploymentSpecValidator());
        execution.run(new ValidationOverridesValidator());
        execution.run(new ConstantValidator());
        execution.run(new SecretStoreValidator());
        execution.run(new EndpointCertificateSecretsValidator());
        execution.run(new AccessControlFilterValidator());
        execution.run(new QuotaValidator());
        execution.run(new UriBindingsValidator());
        execution.run(new CloudDataPlaneFilterValidator());
        execution.run(new AccessControlFilterExcludeValidator());
        execution.run(new CloudUserFilterValidator());
        execution.run(new CloudHttpConnectorValidator());
        execution.run(new UrlConfigValidator());
        execution.run(new JvmHeapSizeValidator());
    }

    private static void validateFirstTimeDeployment(Execution execution) {
        execution.run(new RedundancyValidator());
    }

    private static void validateChanges(VespaModel currentModel, Execution execution) {
        execution.run(new IndexingModeChangeValidator(), currentModel);
        execution.run(new GlobalDocumentChangeValidator(), currentModel);
        execution.run(new IndexedSearchClusterChangeValidator(), currentModel);
        execution.run(new StreamingSearchClusterChangeValidator(), currentModel);
        execution.run(new ConfigValueChangeValidator(), currentModel);
        execution.run(new StartupCommandChangeValidator(), currentModel);
        execution.run(new ContentTypeRemovalValidator(), currentModel);
        execution.run(new ContentClusterRemovalValidator(), currentModel);
        execution.run(new ResourcesReductionValidator(), currentModel);
        execution.run(new ContainerRestartValidator(), currentModel);
        execution.run(new NodeResourceChangeValidator(), currentModel);
        execution.run(new RedundancyIncreaseValidator(), currentModel);
        execution.run(new CertificateRemovalChangeValidator(), currentModel);
        execution.run(new RedundancyValidator(), currentModel);
        execution.run(new RestartOnDeployForOnnxModelChangesValidator(), currentModel);
    }

    private static void deferConfigChangesForClustersToBeRestarted(List<ConfigChangeAction> actions, VespaModel model) {
        Set<ClusterSpec.Id> clustersToBeRestarted = actions.stream()
                                                           .filter(action -> action.getType() == ConfigChangeAction.Type.RESTART)
                                                           .map(action -> action.clusterId())
                                                           .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
        for (var clusterToRestart : clustersToBeRestarted) {
            var containerCluster = model.getContainerClusters().get(clusterToRestart.value());
            if (containerCluster != null)
                containerCluster.setDeferChangesUntilRestart(true);

            var contentCluster = model.getContentClusters().get(clusterToRestart.value());
            if (contentCluster != null)
                contentCluster.setDeferChangesUntilRestart(true);
        }
    }


    private static class Execution {

        private final Map<ValidationId, List<String>> failures = new LinkedHashMap<>();
        private final VespaModel model;
        private final DeployState deployState;
        private final List<ConfigChangeAction> actions = new ArrayList<>();

        private Execution(VespaModel model, DeployState deployState) {
            this.model = model;
            this.deployState = deployState;
        }

        private void run(Validator validator) {
            try {
                validator.validate(model, deployState);
            }
            catch (ValidationException e) {
                e.messagesById().forEach((id, messages) -> failures.computeIfAbsent(id, __ -> new ArrayList<>()).addAll(messages));
            }
        }

        private void run(ChangeValidator validator, VespaModel previousModel) {
            try {
                // Some change validators throw, while some return a list of changes that may again be disallowed.
                for (ConfigChangeAction action : validator.validate(previousModel, model, deployState)) {
                    actions.add(action);
                    if (action.validationId().isPresent()) run(new Validator() { // Changes without a validation ID are always allowed.
                        @Override public void validate(VespaModel model, DeployState deployState) {
                            deployState.validationOverrides().invalid(action.validationId().get(), action.getMessage(), deployState.now());
                        }
                    });
                }
            }
            catch (ValidationException e) {
                e.messagesById().forEach((id, messages) -> failures.computeIfAbsent(id, __ -> new ArrayList<>()).addAll(messages));
            }
        }

        private void throwIfFailed() {
            try {
                if (failures.size() == 1 && failures.values().iterator().next().size() == 1) // Retain single-form exception message when possible.
                    deployState.validationOverrides().invalid(failures.keySet().iterator().next(), failures.values().iterator().next().get(0), deployState.now());
                else
                    deployState.validationOverrides().invalid(failures, deployState.now());
            }
            catch (ValidationException e) {
                if (deployState.isHosted() && deployState.zone().environment().isManuallyDeployed())
                    deployState.getDeployLogger().logApplicationPackage(Level.WARNING,
                                                                        "Auto-overriding validation which would be disallowed in production: " +
                                                                        Exceptions.toMessageString(e));
                else throw e;
            }
        }

    }

}
