// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.vespa.filedistribution.FileDistributionManager;

import java.util.*;

/**
 * Implements invoker of filedistribution using manager with JNI.
 *
 * @author tonytv
 * @author lulf
 * @since 5.1.14
 */
public class FileDBHandler implements FileDistribution {
    private final FileDistributionManager manager;

    public FileDBHandler(FileDistributionManager manager) {
        this.manager = manager;
    }

    @Override
    public void sendDeployedFiles(String hostName, Set<FileReference> fileReferences) {
        List<String> referencesAsString = new ArrayList<>();
        for (FileReference reference : fileReferences) {
            referencesAsString.add(reference.value());
        }
        manager.setDeployedFiles(hostName, referencesAsString);
    }

    @Override
    public void limitSendingOfDeployedFilesTo(Collection<String> hostNames) {
        manager.limitSendingOfDeployedFilesTo(hostNames);
    }

    @Override
    public void removeDeploymentsThatHaveDifferentApplicationId(Collection<String> targetHostnames) {
        manager.removeDeploymentsThatHaveDifferentApplicationId(targetHostnames);
    }

    @Override
    public void reloadDeployFileDistributor() {
        manager.reloadDeployFileDistributor();
    }
}
