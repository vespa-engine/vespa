// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;

// Note: Unit test is in ApplicationRepositoryTest
public class FileDistributionMaintainer extends Maintainer {

    private final ApplicationRepository applicationRepository;
    private final File fileReferencesDir;
    private final ConfigserverConfig configserverConfig;

    FileDistributionMaintainer(ApplicationRepository applicationRepository,
                               Curator curator,
                               Duration interval,
                               ConfigserverConfig configserverConfig) {
        super(applicationRepository, curator, interval);
        this.applicationRepository = applicationRepository;
        this.configserverConfig = configserverConfig;
        this.fileReferencesDir = new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));;
    }


    @Override
    protected void maintain() {
        // TODO: For now only deletes files in CD system
        boolean deleteFiles = SystemName.from(configserverConfig.system()) == SystemName.cd;
        applicationRepository.deleteUnusedFiledistributionReferences(fileReferencesDir, deleteFiles);
    }
}
