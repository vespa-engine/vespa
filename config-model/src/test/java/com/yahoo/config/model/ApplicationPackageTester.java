// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.component.Version;
import com.yahoo.config.model.application.provider.ApplicationPackageXmlFilesValidator;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Helper for tests using a file application package
 *
 * @author bratseth
 */
public class ApplicationPackageTester {

    private final FilesApplicationPackage applicationPackage;

    private ApplicationPackageTester(String applicationPackageDir, boolean validate) {
        try {
            FilesApplicationPackage applicationPackage =
                    FilesApplicationPackage.fromFile(new File(applicationPackageDir));
            if (validate) {
                ApplicationPackageXmlFilesValidator validator =
                        ApplicationPackageXmlFilesValidator.create(new File(applicationPackageDir), new Version(6));
                validator.checkApplication();
                validator.checkIncludedDirs(applicationPackage);
            }
            this.applicationPackage = applicationPackage;
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not create an application package from '" + applicationPackageDir + "'", e);
        }
    }

    public FilesApplicationPackage app() { return applicationPackage; }

    public List<SearchDefinition> getSearchDefinitions() {
        return new DeployState.Builder().applicationPackage(app()).build().getSearchDefinitions();
    }

    public static ApplicationPackageTester create(String applicationPackageDir) {
        return new ApplicationPackageTester(applicationPackageDir, true);
    }

}
