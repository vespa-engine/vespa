// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;

public class FileDistributionMaintainer extends Maintainer {

    private final ApplicationRepository applicationRepository;
    private final File fileReferencesDir;

    public FileDistributionMaintainer(ApplicationRepository applicationRepository,
                                      Curator curator,
                                      Duration interval,
                                      ConfigserverConfig configserverConfig) {
        super(applicationRepository, curator, interval);
        this.applicationRepository = applicationRepository;
        this.fileReferencesDir = new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));;
    }


    @Override
    protected void maintain() {
        // TODO: Does not delete, for now just outputs what should be deleted
        applicationRepository.deleteUnusedFiledistributionReferences(fileReferencesDir, false);
    }
}
