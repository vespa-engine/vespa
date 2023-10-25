// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.filedistribution.FileDirectory;
import com.yahoo.vespa.curator.Curator;

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

    private final FileDirectory fileDirectory;
    private final Duration maxUnusedFileReferenceAge;

    FileDistributionMaintainer(ApplicationRepository applicationRepository,
                               Curator curator,
                               Duration interval,
                               FileDirectory fileDirectory) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(), interval, false);
        ConfigserverConfig configserverConfig = applicationRepository.configserverConfig();
        this.maxUnusedFileReferenceAge = Duration.ofMinutes(configserverConfig.keepUnusedFileReferencesMinutes());
        this.fileDirectory = fileDirectory;
    }

    @Override
    protected double maintain() {
        applicationRepository.deleteUnusedFileDistributionReferences(fileDirectory, maxUnusedFileReferenceAge);
        return 1.0;
    }

}
