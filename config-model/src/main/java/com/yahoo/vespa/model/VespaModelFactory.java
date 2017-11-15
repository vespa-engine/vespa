// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ConfigModelPlugin;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.application.provider.ApplicationPackageXmlFilesValidator;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Version;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.VespaVersion;
import com.yahoo.vespa.model.application.validation.Validation;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Factory for creating {@link VespaModel} instances.
 *
 * @author Ulf Lilleengen
 */
public class VespaModelFactory implements ModelFactory {

    private static final Logger log = Logger.getLogger(VespaModelFactory.class.getName());
    private final ConfigModelRegistry configModelRegistry;
    private final Zone zone;
    private final Clock clock;
    private final Version version;

    /** Creates a factory for vespa models for this version of the source */
    @Inject
    public VespaModelFactory(ComponentRegistry<ConfigModelPlugin> pluginRegistry, Zone zone) {
        this(Version.fromIntValues(VespaVersion.major, VespaVersion.minor, VespaVersion.micro), pluginRegistry, zone);
    }

    /** Creates a factory for vespa models of a particular version */
    public VespaModelFactory(Version version, ComponentRegistry<ConfigModelPlugin> pluginRegistry, Zone zone) {
        this.version = version;
        List<ConfigModelBuilder> modelBuilders = new ArrayList<>();
        for (ConfigModelPlugin plugin : pluginRegistry.allComponents()) {
            if (plugin instanceof ConfigModelBuilder) {
                modelBuilders.add((ConfigModelBuilder) plugin);
            }
        }
        this.configModelRegistry = new MapConfigModelRegistry(modelBuilders);
        this.zone = zone;
        this.clock = Clock.systemUTC();
    }
    
    public VespaModelFactory(ConfigModelRegistry configModelRegistry) {
        this(configModelRegistry, Clock.systemUTC());
    }
    public VespaModelFactory(ConfigModelRegistry configModelRegistry, Clock clock) {
        this(Version.fromIntValues(VespaVersion.major, VespaVersion.minor, VespaVersion.micro), configModelRegistry, clock);
    }
    public VespaModelFactory(Version version, ConfigModelRegistry configModelRegistry, Clock clock) {
        this.version = version;
        if (configModelRegistry == null) {
            this.configModelRegistry = new NullConfigModelRegistry();
            log.info("Will not load config models from plugins, as no registry is available");
        } else {
            this.configModelRegistry = configModelRegistry;
        }
        this.zone = Zone.defaultZone();
        this.clock = clock;
    }

    /** Returns the version this model is build for */
    @Override
    public Version getVersion() { return version; }

    @Override
    public Model createModel(ModelContext modelContext) {
        return buildModel(createDeployState(modelContext));
    }

    @Override
    public ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors) {
        validateXml(modelContext, ignoreValidationErrors);
        DeployState deployState = createDeployState(modelContext);
        VespaModel model = buildModel(deployState);
        List<ConfigChangeAction> changeActions = validateModel(model, deployState, ignoreValidationErrors);
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
            throw new RuntimeException(e);
        }
    }

    private DeployState createDeployState(ModelContext modelContext) {
        DeployState.Builder builder = new DeployState.Builder()
            .applicationPackage(modelContext.applicationPackage())
            .deployLogger(modelContext.deployLogger())
            .configDefinitionRepo(modelContext.configDefinitionRepo())
            .fileRegistry(modelContext.getFileRegistry())
            .permanentApplicationPackage(modelContext.permanentApplicationPackage())
            .properties(createDeployProperties(modelContext.properties()))
            .modelHostProvisioner(createHostProvisioner(modelContext))
            .rotations(modelContext.properties().rotations())
            .zone(zone)
            .now(clock.instant())
            .wantedNodeVespaVersion(modelContext.wantedNodeVespaVersion());
        modelContext.previousModel().ifPresent(builder::previousModel);
        return builder.build();
    }

    private DeployProperties createDeployProperties(ModelContext.Properties properties) {
        return new DeployProperties.Builder()
                .applicationId(properties.applicationId())
                .configServerSpecs(properties.configServerSpecs())
                .loadBalancerName(properties.loadBalancerName())
                .multitenant(properties.multitenant())
                .hostedVespa(properties.hostedVespa())
                .vespaVersion(getVersion())
                .zone(properties.zone())
                .build();
    }

    private static HostProvisioner createHostProvisioner(ModelContext modelContext) {
        return modelContext.hostProvisioner().orElse(
                DeployState.getDefaultModelHostProvisioner(modelContext.applicationPackage()));
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

    private List<ConfigChangeAction> validateModel(VespaModel model, DeployState deployState, boolean ignoreValidationErrors) {
        try {
            return Validation.validate(model, ignoreValidationErrors, deployState);
        } catch (IllegalArgumentException e) {
            rethrowUnlessIgnoreErrors(e, ignoreValidationErrors);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ArrayList<>();
    }

    private static void rethrowUnlessIgnoreErrors(IllegalArgumentException e, boolean ignoreValidationErrors) {
        if (!ignoreValidationErrors) {
            throw e;
        }
    }

}
