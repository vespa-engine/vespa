// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.util.logging.Level;

/**
 * Validates that correct directory is used for schemas
 *
 * @author hmusum
 */
public class SchemasDirValidator implements Validator {

    @Override
    public void validate(Context context) {
        ApplicationPackage app = context.deployState().getApplicationPackage();
        ApplicationFile sdDir = app.getFile(ApplicationPackage.SEARCH_DEFINITIONS_DIR);
        if (sdDir.exists() && sdDir.isDirectory())
            context.deployState().getDeployLogger().logApplicationPackage(
                    Level.WARNING,
                    "Directory " + ApplicationPackage.SEARCH_DEFINITIONS_DIR.getRelative() +
                    "/ should not be used for schemas, use " + ApplicationPackage.SCHEMAS_DIR.getRelative() + "/ instead");
    }

}
