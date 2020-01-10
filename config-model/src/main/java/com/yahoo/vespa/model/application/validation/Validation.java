// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ClusterSizeReductionValidator;
import com.yahoo.vespa.model.application.validation.change.ConfigValueChangeValidator;
import com.yahoo.vespa.model.application.validation.change.ContainerRestartValidator;
import com.yahoo.vespa.model.application.validation.change.ContentClusterRemovalValidator;
import com.yahoo.vespa.model.application.validation.change.ContentTypeRemovalValidator;
import com.yahoo.vespa.model.application.validation.change.GlobalDocumentChangeValidator;
import com.yahoo.vespa.model.application.validation.change.IndexedSearchClusterChangeValidator;
import com.yahoo.vespa.model.application.validation.change.IndexingModeChangeValidator;
import com.yahoo.vespa.model.application.validation.change.StartupCommandChangeValidator;
import com.yahoo.vespa.model.application.validation.change.StreamingSearchClusterChangeValidator;
import com.yahoo.vespa.model.application.validation.first.AccessControlOnFirstDeploymentValidator;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * Executor of validators. This defines the right order of validator execution.
 *
 * @author hmusum
 */
public class Validation {

    /**
     * Validates the model supplied, and if there already exists a model for the application validates changes
     * between the previous and current model
     *
     * @return a list of required changes needed to make this configuration live
     */
    public static List<ConfigChangeAction> validate(VespaModel model, ValidationParameters validationParameters, DeployState deployState) {
        if (validationParameters.checkRouting()) {
            new RoutingValidator().validate(model, deployState);
            new RoutingSelectorValidator().validate(model, deployState);
        }
        new ComponentValidator().validate(model, deployState);
        new SearchDataTypeValidator().validate(model, deployState);
        new ComplexAttributeFieldsValidator().validate(model, deployState);
        new StreamingValidator().validate(model, deployState);
        new RankSetupValidator(validationParameters.ignoreValidationErrors()).validate(model, deployState);
        new NoPrefixForIndexes().validate(model, deployState);
        new DeploymentSpecValidator().validate(model, deployState);
        new RankingConstantsValidator().validate(model, deployState);
        new SecretStoreValidator().validate(model, deployState);
        new TlsSecretsValidator().validate(model, deployState);

        List<ConfigChangeAction> result = Collections.emptyList();
        if (deployState.getProperties().isFirstTimeDeployment()) {
            validateFirstTimeDeployment(model, deployState);
        } else {
            Optional<Model> currentActiveModel = deployState.getPreviousModel();
            if (currentActiveModel.isPresent() && (currentActiveModel.get() instanceof VespaModel))
                result = validateChanges((VespaModel) currentActiveModel.get(), model,
                                         deployState.validationOverrides(), deployState.getDeployLogger(), deployState.now());
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
                new ContainerRestartValidator(),
        };
        return Arrays.stream(validators)
                .flatMap(v -> v.validate(currentModel, nextModel, overrides, now).stream())
                .collect(toList());
    }

    private static void validateFirstTimeDeployment(VespaModel model, DeployState deployState) {
        new AccessControlOnFirstDeploymentValidator().validate(model, deployState);
    }

}
