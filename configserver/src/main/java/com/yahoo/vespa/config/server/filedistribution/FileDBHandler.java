// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.vespa.filedistribution.FileDistributionManager;

import java.util.*;

/**
 * Implements invoker of filedistribution using manager with JNI.
 *
 * @author Tony Vaagenes
 * @author Ulf Lilleengen
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

    public void startDownload(String hostName, Set<FileReference> fileReferences) {
        throw new UnsupportedOperationException("Not valid for this Filedistribution implementation");
    }

    @Override
    public void startDownload(String hostName, int port, Set<FileReference> fileReferences) {
        throw new UnsupportedOperationException("Not valid for this Filedistribution implementation");
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
