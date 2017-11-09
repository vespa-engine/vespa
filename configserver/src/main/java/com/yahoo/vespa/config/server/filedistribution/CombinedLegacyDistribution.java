// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;

import java.util.Collection;
import java.util.Set;

public class CombinedLegacyDistribution implements FileDistribution {
    private final FileDistribution legacy;

    CombinedLegacyDistribution(FileDBHandler legacy) {
        this.legacy = legacy;
    }
    @Override
    public void sendDeployedFiles(String hostName, Set<FileReference> fileReferences) {
        legacy.sendDeployedFiles(hostName, fileReferences);
    }

    @Override
    public void reloadDeployFileDistributor() {
        legacy.reloadDeployFileDistributor();
    }

    @Override
    public void removeDeploymentsThatHaveDifferentApplicationId(Collection<String> targetHostnames) {
        legacy.removeDeploymentsThatHaveDifferentApplicationId(targetHostnames);
    }
}
