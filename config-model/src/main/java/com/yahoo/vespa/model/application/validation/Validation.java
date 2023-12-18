// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.application.api.ValidationOverrides.ValidationException;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.Model;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

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
            execution.run(new RoutingValidator());
            execution.run(new RoutingSelectorValidator());
        }
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

        additionalValidators.forEach(execution::run);

        List<ConfigChangeAction> result = Collections.emptyList();
        if (deployState.getProperties().isFirstTimeDeployment()) {
            validateFirstTimeDeployment(execution);
        } else {
            Optional<Model> currentActiveModel = deployState.getPreviousModel();
            if (currentActiveModel.isPresent() && (currentActiveModel.get() instanceof VespaModel)) {
                result = validateChanges((VespaModel) currentActiveModel.get(), execution);
                deferConfigChangesForClustersToBeRestarted(result, model);
            }
        }
        execution.throwIfFailed();
        return result;
    }

    private static List<ConfigChangeAction> validateChanges(VespaModel currentModel, Execution execution) {
        ChangeValidator[] validators = new ChangeValidator[] {
                new IndexingModeChangeValidator(),
                new GlobalDocumentChangeValidator(),
                new IndexedSearchClusterChangeValidator(),
                new StreamingSearchClusterChangeValidator(),
                new ConfigValueChangeValidator(),
                new StartupCommandChangeValidator(),
                new ContentTypeRemovalValidator(),
                new ContentClusterRemovalValidator(),
                new ResourcesReductionValidator(),
                new ResourcesReductionValidator(),
                new ContainerRestartValidator(),
                new NodeResourceChangeValidator(),
                new RedundancyIncreaseValidator(),
                new CertificateRemovalChangeValidator(),
                new RedundancyValidator(),
                new RestartOnDeployForOnnxModelChangesValidator(),
        };
        List<ConfigChangeAction> actions = Arrays.stream(validators)
                                                 .flatMap(v -> v.validate(currentModel, execution.model, execution.deployState).stream())
                                                 .toList();

        execution.runChanges(actions);
        return actions;
    }

    private static void validateFirstTimeDeployment(Execution execution) {
        execution.run(new RedundancyValidator());
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

        private void runChanges(List<ConfigChangeAction> actions) {
            for (ConfigChangeAction action : actions) {
                if (action.validationId().isPresent()) run(new Validator() { // Changes without a validation ID are always allowed.
                    @Override public void validate(VespaModel model, DeployState deployState) {
                        deployState.validationOverrides().invalid(action.validationId().get(), action.getMessage(), deployState.now());
                    }
                });
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
