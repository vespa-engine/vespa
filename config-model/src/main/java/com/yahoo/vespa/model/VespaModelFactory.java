// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import ai.vespa.rankingexpression.importer.lightgbm.LightGBMImporter;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import ai.vespa.rankingexpression.importer.vespa.VespaImporter;
import ai.vespa.rankingexpression.importer.xgboost.XGBoostImporter;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.Version;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigModelPlugin;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.application.provider.ApplicationPackageXmlFilesValidator;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.QuotaExceededException;
import com.yahoo.config.provision.TransientException;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.VespaVersion;
import com.yahoo.vespa.model.application.validation.Validation;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.yolean.Exceptions;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for creating {@link VespaModel} instances.
 *
 * @author Ulf Lilleengen
 */
public class VespaModelFactory implements ModelFactory {

    private static final Logger log = Logger.getLogger(VespaModelFactory.class.getName());
    private final ConfigModelRegistry configModelRegistry;
    private final Collection<MlModelImporter> modelImporters;
    private final Zone zone;
    private final Clock clock;
    private final Version version;
    private final List<Validator> additionalValidators;

    /** Creates a factory for Vespa models for this version of the source */
    @Inject
    public VespaModelFactory(ComponentRegistry<ConfigModelPlugin> pluginRegistry,
                             ComponentRegistry<Validator> additionalValidators,
                             Zone zone) {
        this.version = new Version(VespaVersion.major, VespaVersion.minor, VespaVersion.micro);
        List<ConfigModelBuilder<?>> modelBuilders = new ArrayList<>();
        for (ConfigModelPlugin plugin : pluginRegistry.allComponents()) {
            if (plugin instanceof ConfigModelBuilder p) {
                modelBuilders.add(p);
            }
        }
        this.configModelRegistry = new MapConfigModelRegistry(modelBuilders);
        this.modelImporters = List.of(
                new VespaImporter(),
                new OnnxImporter(),
                new TensorFlowImporter(),
                new XGBoostImporter(),
                new LightGBMImporter());
        this.zone = zone;
        this.additionalValidators = List.copyOf(additionalValidators.allComponents());

        this.clock = Clock.systemUTC();
    }

    // For testing only
    protected VespaModelFactory(ConfigModelRegistry configModelRegistry) {
        this(new Version(VespaVersion.major, VespaVersion.minor, VespaVersion.micro), configModelRegistry,
                Clock.systemUTC(), Zone.defaultZone());
    }

    private VespaModelFactory(Version version, ConfigModelRegistry configModelRegistry, Clock clock, Zone zone) {
        this.version = version;
        if (configModelRegistry == null) {
            this.configModelRegistry = new NullConfigModelRegistry();
            log.info("Will not load config models from plugins, as no registry is available");
        } else {
            this.configModelRegistry = configModelRegistry;
        }
        this.modelImporters = Collections.emptyList();
        this.additionalValidators = List.of();
        this.zone = zone;
        this.clock = clock;
    }

    public static VespaModelFactory createTestFactory() {
        return createTestFactory(new NullConfigModelRegistry(), Clock.systemUTC());
    }
    public static VespaModelFactory createTestFactory(ConfigModelRegistry configModelRegistry, Clock clock) {
        return createTestFactory(new Version(VespaVersion.major, VespaVersion.minor, VespaVersion.micro), configModelRegistry,
                clock, Zone.defaultZone());
    }

    public static VespaModelFactory createTestFactory(Version version, ConfigModelRegistry configModelRegistry, Clock clock, Zone zone) {
        return new VespaModelFactory(version, configModelRegistry, clock, zone);
    }

    /** Returns the version this model is build for */
    @Override
    public Version version() { return version; }

    @Override
    public Model createModel(ModelContext modelContext) {
        return buildModel(createDeployState(modelContext, new ValidationParameters(ValidationParameters.IgnoreValidationErrors.TRUE)));
    }

    private void logReindexingReasons(List<ConfigChangeAction> changeActions,
                                      VespaModel nextModel,
                                      Optional<Model> currentActiveModel)
    {
        if (currentActiveModel.isEmpty()) {
            return;
        }
        for (ConfigChangeAction action : changeActions) {
            if (action.getType().equals(ConfigChangeAction.Type.REINDEX)) {
                VespaModel currentModel = (VespaModel) currentActiveModel.get();
                var currentMeta = currentModel.applicationPackage().getMetaData();
                var nextMeta = nextModel.applicationPackage().getMetaData();
                log.log(Level.INFO, String.format("Model [%s/%s] -> [%s/%s] triggers reindexing: %s",
                                                  currentModel.version().toString(), currentMeta.toString(),
                                                  nextModel.version().toString(), nextMeta.toString(),
                                                  action));
            }
        }
    }

    @Override
    public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
        validateXml(modelContext, validationParameters.ignoreValidationErrors());
        DeployState deployState = createDeployState(modelContext, validationParameters);
        VespaModel model = buildModel(deployState);
        List<ConfigChangeAction> changeActions = validateModel(model, deployState, validationParameters);
        logReindexingReasons(changeActions, model, deployState.getPreviousModel());
        return new ModelCreateResult(model, changeActions);
    }
    
    private void validateXml(ModelContext modelContext, boolean ignoreValidationErrors) {
        if (modelContext.appDir().isPresent()) {
            ApplicationPackageXmlFilesValidator validator =
                    ApplicationPackageXmlFilesValidator.create(modelContext.appDir().get(),
                                                               modelContext.modelVespaVersion());
            try {
                validator.checkApplication();
                validator.checkIncludedDirs(modelContext.applicationPackage());
            } catch (IllegalArgumentException e) {
                rethrowUnlessIgnoreErrors(e, ignoreValidationErrors);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            validateXML(modelContext.applicationPackage(), ignoreValidationErrors);
        }
    }

    private VespaModel buildModel(DeployState deployState) {
        try {
            return new VespaModel(configModelRegistry, deployState);
        } catch (IOException | SAXException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private DeployState createDeployState(ModelContext modelContext, ValidationParameters validationParameters) {
        DeployState.Builder builder = new DeployState.Builder()
            .applicationPackage(modelContext.applicationPackage())
            .deployLogger(modelContext.deployLogger())
            .configDefinitionRepo(modelContext.configDefinitionRepo())
            .fileRegistry(modelContext.getFileRegistry())
            .executor(modelContext.getExecutor())
            .properties(modelContext.properties())
            .vespaVersion(version())
            .modelHostProvisioner(modelContext.getHostProvisioner())
            .provisioned(modelContext.provisioned())
            .endpoints(modelContext.properties().endpoints())
            .modelImporters(modelImporters)
            .zone(zone)
            .now(clock.instant())
            .wantedNodeVespaVersion(modelContext.wantedNodeVespaVersion())
            .wantedDockerImageRepo(modelContext.wantedDockerImageRepo());
        modelContext.previousModel().ifPresent(builder::previousModel);
        modelContext.reindexing().ifPresent(builder::reindexing);
        return builder.build(validationParameters);
    }

    private void validateXML(ApplicationPackage applicationPackage, boolean ignoreValidationErrors) {
        try {
            applicationPackage.validateXML();
        } catch (IllegalArgumentException e) {
            rethrowUnlessIgnoreErrors(e, ignoreValidationErrors);
        } catch (Exception e) {
             throw new RuntimeException(e);
        }
    }

    private List<ConfigChangeAction> validateModel(VespaModel model, DeployState deployState, ValidationParameters validationParameters) {
        try {
            return new Validation(additionalValidators).validate(model, validationParameters, deployState);
        } catch (ValidationOverrides.ValidationException e) {
            if (deployState.isHosted() && zone.environment().isManuallyDeployed())
                deployState.getDeployLogger().logApplicationPackage(Level.WARNING,
                                                                    "Auto-overriding validation which would be disallowed in production: " +
                                                                    Exceptions.toMessageString(e));
            else
                rethrowUnlessIgnoreErrors(e, validationParameters.ignoreValidationErrors());
        } catch (IllegalArgumentException | TransientException | QuotaExceededException e) {
            rethrowUnlessIgnoreErrors(e, validationParameters.ignoreValidationErrors());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ArrayList<>();
    }

    private static void rethrowUnlessIgnoreErrors(RuntimeException e, boolean ignoreValidationErrors) {
        if (!ignoreValidationErrors) {
            throw e;
        }
    }

}
