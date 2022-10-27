// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.FlagSource;

import java.io.File;
import java.time.Duration;

/**
 * Removes unused file references older than a configured time, but always keeps a certain number of file references
 * even when they are unused.
 * <p>
 * Note: Unit test is in ApplicationRepositoryTest
 *
 * @author hmusum
 */
public class FileDistributionMaintainer extends ConfigServerMaintainer {

    private final ApplicationRepository applicationRepository;
    private final File fileReferencesDir;
    private final Duration maxUnusedFileReferenceAge;

    FileDistributionMaintainer(ApplicationRepository applicationRepository,
                               Curator curator,
                               Duration interval,
                               FlagSource flagSource) {
        super(applicationRepository, curator, flagSource, applicationRepository.clock().instant(), interval, false);
        this.applicationRepository = applicationRepository;
        ConfigserverConfig configserverConfig = applicationRepository.configserverConfig();
        this.maxUnusedFileReferenceAge = Duration.ofMinutes(configserverConfig.keepUnusedFileReferencesMinutes());
        this.fileReferencesDir = new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir()));
    }

    @Override
    protected double maintain() {
        applicationRepository.deleteUnusedFileDistributionReferences(fileReferencesDir, maxUnusedFileReferenceAge);
        return 1.0;
    }

}
