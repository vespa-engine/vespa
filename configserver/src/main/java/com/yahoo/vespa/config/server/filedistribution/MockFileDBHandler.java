// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;

import java.util.Collection;
import java.util.Set;

/**
 * @author lulf
 * @since 5.1
 */
public class MockFileDBHandler implements FileDistribution {
    public int sendDeployedFilesCalled = 0;
    public int reloadDeployFileDistributorCalled = 0;
    public int limitSendingOfDeployedFilesToCalled = 0;
    public int removeDeploymentsThatHaveDifferentApplicationIdCalled = 0;

    @Override
    public void sendDeployedFiles(String hostName, Set<FileReference> fileReferences) {
        sendDeployedFilesCalled++;
    }

    @Override
    public void reloadDeployFileDistributor() {
        reloadDeployFileDistributorCalled++;
    }

    @Override
    public void limitSendingOfDeployedFilesTo(Collection<String> hostNames) {
        limitSendingOfDeployedFilesToCalled++;
    }

    @Override
    public void removeDeploymentsThatHaveDifferentApplicationId(Collection<String> targetHostnames) {
        removeDeploymentsThatHaveDifferentApplicationIdCalled++;
    }
}
