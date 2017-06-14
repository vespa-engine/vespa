// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.log.LogLevel;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A global permanent application package containing configuration info that is always used during deploy.
 *
 * @author lulf
 * @since 5.15
 */
public class PermanentApplicationPackage {

    private static final Logger log = Logger.getLogger(PermanentApplicationPackage.class.getName());
    private final Optional<ApplicationPackage> applicationPackage;

    public PermanentApplicationPackage(ConfigserverConfig config) {
        File app = new File(Defaults.getDefaults().underVespaHome(config.applicationDirectory()));
        applicationPackage = Optional.<ApplicationPackage>ofNullable(app.exists() ? FilesApplicationPackage.fromFile(app) : null);
        if (applicationPackage.isPresent()) {
            log.log(LogLevel.DEBUG, "Detected permanent application package in '" +
                                    Defaults.getDefaults().underVespaHome(config.applicationDirectory()) +
                                    "'. This might add extra services to config models");
        }
    }

    /**
     * Get the permanent application package.
     *
     * @return An {@link Optional} of the application package, as it may not exist.
     */
    public Optional<ApplicationPackage> applicationPackage() {
        return applicationPackage;
    }

}
