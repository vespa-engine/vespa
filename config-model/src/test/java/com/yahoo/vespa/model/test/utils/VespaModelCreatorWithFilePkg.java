// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test.utils;

import com.yahoo.component.Version;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.application.provider.*;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation;

import java.io.File;
import java.io.IOException;

/**
 * For testing purposes only
 *
 * @author tonytv
 */
public class VespaModelCreatorWithFilePkg {

    private FilesApplicationPackage applicationPkg;

    private ConfigModelRegistry configModelRegistry;

    public VespaModelCreatorWithFilePkg(String directoryName) {
        this(new File(directoryName));
    }

    public VespaModelCreatorWithFilePkg(File directory) {
        this(directory, new NullConfigModelRegistry());
    }

    public VespaModelCreatorWithFilePkg(String directoryName, ConfigModelRegistry configModelRegistry) {
        this(new File(directoryName), configModelRegistry);
    }

    public VespaModelCreatorWithFilePkg(File directory, ConfigModelRegistry configModelRegistry) {
        this.configModelRegistry = configModelRegistry;
        this.applicationPkg = FilesApplicationPackage.fromFile(directory);
    }

    public VespaModel create() {
        return create(true);
    }

    public void validate() throws IOException {
        ApplicationPackageXmlFilesValidator validator =
                ApplicationPackageXmlFilesValidator.create(applicationPkg.getAppDir(), new Version(6));
        validator.checkApplication();
        validator.checkIncludedDirs(applicationPkg);
    }

    public VespaModel create(boolean validateApplicationWithSchema) {
        try {
            if (validateApplicationWithSchema) {
                validate();
            }
            DeployState deployState = new DeployState.Builder().applicationPackage(applicationPkg).build(true);
            VespaModel model = new VespaModel(configModelRegistry, deployState);
            // Validate, but without checking configSources or routing (routing
            // is constructed in a special way and cannot always be validated in
            // this step for unit tests)
            Validation.validate(model, false, false, deployState);
            return model;
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

}
