// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.api.ValidationParameters.CheckRouting;
import com.yahoo.config.model.application.provider.SchemaValidators;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation;

import java.util.List;
import java.util.Map;

/**
 * For testing purposes only.
 *
 * @author Tony Vaagenes
 */
public class VespaModelCreatorWithMockPkg {

    public final ApplicationPackage appPkg;
    public DeployState deployState = null;
    public List<ConfigChangeAction> configChangeActions;

    public VespaModelCreatorWithMockPkg(String hosts, String services) {
        this(new MockApplicationPackage.Builder().withHosts(hosts).withServices(services).build());
    }

    public VespaModelCreatorWithMockPkg(String hosts, String services, List<String> schemas) {
        this(hosts, services, schemas, Map.of());
    }

    public VespaModelCreatorWithMockPkg(String hosts, String services, List<String> schemas, Map<Path, String> files) {
        this(new MockApplicationPackage.Builder().withHosts(hosts)
                                                 .withServices(services)
                                                 .withSchemas(schemas)
                                                 .withFiles(files)
                                                 .build());
    }

    public VespaModelCreatorWithMockPkg(ApplicationPackage appPkg) {
        this.appPkg = appPkg;
    }

    public VespaModel create() {
        DeployState deployState = new DeployState.Builder().applicationPackage(appPkg).build();
        return create(true, deployState);
    }

    public VespaModel create(DeployState.Builder deployStateBuilder) {
        return create(true, deployStateBuilder.applicationPackage(appPkg).build());
    }

    public VespaModel create(boolean validate, DeployState deployState) {
        return create(validate, deployState, new NullConfigModelRegistry());
    }

    public VespaModel create(boolean validate, DeployState deployState, ConfigModelRegistry configModelRegistry) {
        try {
            this.deployState = deployState;
            VespaModel model = new VespaModel(configModelRegistry, deployState);
            Version vespaVersion = new Version(6);
            if (validate) {
                SchemaValidators validators = new SchemaValidators(vespaVersion);
                try {
                    if (appPkg.getHosts() != null) {
                        validators.hostsXmlValidator().validate(appPkg.getHosts());
                    }
                    if (appPkg.getDeployment().isPresent()) {
                        validators.deploymentXmlValidator().validate(appPkg.getDeployment().get());
                    }
                    validators.servicesXmlValidator().validate(appPkg.getServices());
                } catch (Exception e) {
                    System.err.println(e.getClass());
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                // Validate, but without checking configSources or routing (routing
                // is constructed in a special way and cannot always be validated in
                // this step for unit tests)
                ValidationParameters validationParameters = new ValidationParameters(CheckRouting.FALSE);
                configChangeActions = new Validation().validate(model, validationParameters, deployState);
            }
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

}
