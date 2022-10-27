// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.application.validation.change.CloudAccountChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ClusterSizeReductionValidator;
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
import com.yahoo.vespa.model.application.validation.change.StartupCommandChangeValidator;
import com.yahoo.vespa.model.application.validation.change.StreamingSearchClusterChangeValidator;
import com.yahoo.vespa.model.application.validation.first.RedundancyOnFirstDeploymentValidator;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * Executor of validators. This defines the right order of validator execution.
 *
 * @author hmusum
 */
public class Validation {

    private final List<Validator> additionalValidators;

    public Validation() { this(List.of()); }

    /** Create instance taking additional validators (e.g for cloud applications) */
    public Validation(List<Validator> additionalValidators) { this.additionalValidators = additionalValidators; }

    /**
     * Validates the model supplied, and if there already exists a model for the application validates changes
     * between the previous and current model
     *
     * @return a list of required changes needed to make this configuration live
     * @throws ValidationOverrides.ValidationException if the change fails validation
     */
    public List<ConfigChangeAction> validate(VespaModel model, ValidationParameters validationParameters, DeployState deployState) {
        if (validationParameters.checkRouting()) {
            new RoutingValidator().validate(model, deployState);
            new RoutingSelectorValidator().validate(model, deployState);
        }
        new SchemasDirValidator().validate(model, deployState);
        new BundleValidator().validate(model, deployState);
        new SearchDataTypeValidator().validate(model, deployState);
        new ComplexFieldsWithStructFieldAttributesValidator().validate(model, deployState);
        new ComplexFieldsWithStructFieldIndexesValidator().validate(model, deployState);
        new StreamingValidator().validate(model, deployState);
        new RankSetupValidator(validationParameters.ignoreValidationErrors()).validate(model, deployState);
        new NoPrefixForIndexes().validate(model, deployState);
        new DeploymentSpecValidator().validate(model, deployState);
        new ValidationOverridesValidator().validate(model, deployState);
        new ConstantValidator().validate(model, deployState);
        new SecretStoreValidator().validate(model, deployState);
        new EndpointCertificateSecretsValidator().validate(model, deployState);
        new AccessControlFilterValidator().validate(model, deployState);
        new CloudWatchValidator().validate(model, deployState);
        new QuotaValidator().validate(model, deployState);
        new UriBindingsValidator().validate(model, deployState);

        additionalValidators.forEach(v -> v.validate(model, deployState));

        List<ConfigChangeAction> result = Collections.emptyList();
        if (deployState.getProperties().isFirstTimeDeployment()) {
            validateFirstTimeDeployment(model, deployState);
        } else {
            Optional<Model> currentActiveModel = deployState.getPreviousModel();
            if (currentActiveModel.isPresent() && (currentActiveModel.get() instanceof VespaModel)) {
                result = validateChanges((VespaModel) currentActiveModel.get(), model,
                                         deployState.validationOverrides(), deployState.getDeployLogger(), deployState.now());
                deferConfigChangesForClustersToBeRestarted(result, model);
            }
        }
        return result;
    }

    private static List<ConfigChangeAction> validateChanges(VespaModel currentModel, VespaModel nextModel,
                                                            ValidationOverrides overrides, DeployLogger logger,
                                                            Instant now) {
        ChangeValidator[] validators = new ChangeValidator[] {
                new IndexingModeChangeValidator(),
                new GlobalDocumentChangeValidator(),
                new IndexedSearchClusterChangeValidator(),
                new StreamingSearchClusterChangeValidator(),
                new ConfigValueChangeValidator(logger),
                new StartupCommandChangeValidator(),
                new ContentTypeRemovalValidator(),
                new ContentClusterRemovalValidator(),
                new ClusterSizeReductionValidator(),
                new ResourcesReductionValidator(),
                new ContainerRestartValidator(),
                new NodeResourceChangeValidator(),
                new RedundancyIncreaseValidator(),
                new CloudAccountChangeValidator()
        };
        List<ConfigChangeAction> actions = Arrays.stream(validators)
                                                 .flatMap(v -> v.validate(currentModel, nextModel, overrides, now).stream())
                                                 .collect(toList());

        Map<ValidationId, Collection<String>> disallowableActions = actions.stream()
                                                                           .filter(action -> action.validationId().isPresent())
                                                                           .collect(groupingBy(action -> action.validationId().orElseThrow(),
                                                                                               mapping(ConfigChangeAction::getMessage,
                                                                                                       toCollection(LinkedHashSet::new))));
        overrides.invalid(disallowableActions, now);
        return actions;
    }

    private static void validateFirstTimeDeployment(VespaModel model, DeployState deployState) {
        new RedundancyOnFirstDeploymentValidator().validate(model, deployState);
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

}
