// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.application.api.ValidationOverrides.ValidationException;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.change.CertificateRemovalChangeValidator;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

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

        for (Validator validator : additionalValidators) {
            validator.validate(execution);
        }

        if (deployState.getProperties().isFirstTimeDeployment()) {
            validateFirstTimeDeployment(execution);
        }
        else if (deployState.getPreviousModel().isPresent() && (deployState.getPreviousModel().get() instanceof VespaModel)) {
            validateChanges(execution);
        }

        execution.throwIfFailed();
        return execution.actions;
    }

    private static void validateRouting(Execution execution) {
        new RoutingValidator().validate(execution);
    }

    private static void validateModel(ValidationParameters validationParameters, Execution execution) {
        new SchemasDirValidator().validate(execution);
        new BundleValidator().validate(execution);
        new PublicApiBundleValidator().validate(execution);
        new SearchDataTypeValidator().validate(execution);
        new ComplexFieldsWithStructFieldAttributesValidator().validate(execution);
        new ComplexFieldsWithStructFieldIndexesValidator().validate(execution);
        new StreamingValidator().validate(execution);
        new RankSetupValidator(validationParameters.ignoreValidationErrors()).validate(execution);
        new NoPrefixForIndexes().validate(execution);
        new ContainerInCloudValidator().validate(execution);
        new DeploymentSpecValidator().validate(execution);
        new ValidationOverridesValidator().validate(execution);
        new ConstantValidator().validate(execution);
        new SecretStoreValidator().validate(execution);
        new AccessControlFilterValidator().validate(execution);
        new QuotaValidator().validate(execution);
        new UriBindingsValidator().validate(execution);
        new CloudDataPlaneFilterValidator().validate(execution);
        new AccessControlFilterExcludeValidator().validate(execution);
        new CloudUserFilterValidator().validate(execution);
        new CloudHttpConnectorValidator().validate(execution);
        new UrlConfigValidator().validate(execution);
        new JvmHeapSizeValidator().validate(execution);
        new InfrastructureDeploymentValidator().validate(execution);
        new EndpointCertificateSecretsValidator().validate(execution);
        new CloudClientsValidator().validate(execution);
    }

    private static void validateFirstTimeDeployment(Execution execution) {
        new RedundancyValidator().validate((Context) execution);
    }

    private static void validateChanges(Execution execution) {
        new IndexingModeChangeValidator().validate(execution);
        new GlobalDocumentChangeValidator().validate(execution);
        new IndexedSearchClusterChangeValidator().validate(execution);
        new StreamingSearchClusterChangeValidator().validate(execution);
        new ConfigValueChangeValidator().validate(execution);
        new StartupCommandChangeValidator().validate(execution);
        new ContentTypeRemovalValidator().validate(execution);
        new ContentClusterRemovalValidator().validate(execution);
        new ResourcesReductionValidator().validate(execution);
        new ContainerRestartValidator().validate(execution);
        new NodeResourceChangeValidator().validate(execution);
        new RedundancyIncreaseValidator().validate(execution);
        new CertificateRemovalChangeValidator().validate(execution);
        new RedundancyValidator().validate(execution);
        new RestartOnDeployForOnnxModelChangesValidator().validate(execution);
    }

    public interface Context {
        /** Auxiliary deploy state of the application. */
        DeployState deployState();
        /** The model to validate. */
        VespaModel model();
        /** Report a failed validation which cannot be overridden; this results in an {@link IllegalArgumentException}. */
        default void illegal(String message) { illegal(message, null); }
        /** Report a failed validation which cannot be overridden; this results in an {@link IllegalArgumentException}. */
        void illegal(String message, Throwable cause);
        /** Report a failed validation which can be overridden; this results in a {@link ValidationException}. */
        void invalid(ValidationId id, String message);
    }

    public interface ChangeContext extends Context {
        /** The previous model, if any. */
        VespaModel previousModel();
        /**
         * Report an action the user must take to change to the new configuration.
         * If the action has a {@link ValidationId}, {@link #invalid} is also called for this id, and the action's message.
         */
        void require(ConfigChangeAction action);
    }

    static class Execution implements ChangeContext {

        private final List<String> errors = new ArrayList<>();
        private final Map<ValidationId, List<String>> failures = new LinkedHashMap<>();
        private final VespaModel model;
        private final DeployState deployState;
        private final List<ConfigChangeAction> actions = new ArrayList<>();

        Execution(VespaModel model, DeployState deployState) {
            this.model = model;
            this.deployState = deployState;
        }

        void throwIfFailed() {
            Optional<ValidationException> invalidException = deployState.validationOverrides().invalidException(failures, deployState.now());
            if (invalidException.isPresent() && deployState.isHosted() && deployState.zone().environment().isManuallyDeployed()) {
                deployState.getDeployLogger().logApplicationPackage(Level.WARNING,
                                                                    "Auto-overriding validation which would be disallowed in production: " +
                                                                    Exceptions.toMessageString(invalidException.get()));
                invalidException = Optional.empty();
            }

            if ( ! errors.isEmpty()) {
                String illegalMessage = errors.size() == 1 ? errors.get(0)
                                                           : "multiple errors:\n\t" + String.join("\n\t", errors);
                if (invalidException.isPresent())
                    illegalMessage += "\n" + invalidException.get().getMessage();

                throw new IllegalArgumentException(illegalMessage);
            }

            invalidException.ifPresent(e -> { throw e; });
        }

        List<ConfigChangeAction> actions() {
            return actions;
        }

        List<String> errors() {
            return errors;
        }

        @Override
        public DeployState deployState() {
            return deployState;
        }

        @Override
        public VespaModel model() {
            return model;
        }

        @Override
        public VespaModel previousModel() {
            return (VespaModel) deployState.getPreviousModel().get();
        }

        @Override
        public void require(ConfigChangeAction action) {
            actions.add(action);
            action.validationId().ifPresent(id -> invalid(id, action.getMessage()));
        }

        @Override
        public void illegal(String message, Throwable cause) {
            if (cause != null) message += ": " + Exceptions.toMessageString(cause);
            errors.add(message);
        }

        @Override
        public void invalid(ValidationId id, String message) {
            failures.computeIfAbsent(id, __ -> new ArrayList<>()).add(message);
        }

    }

}
