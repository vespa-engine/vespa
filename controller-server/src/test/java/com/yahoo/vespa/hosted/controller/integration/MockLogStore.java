// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Collections;

/**
 * @author freva
 */
public class MockLogStore implements LogStore {
    @Override
    public String getTestLog(ApplicationId applicationId, JobType jobType, long buildId) {
        return "SUCCESS";
    }

    @Override
    public void setTestLog(ApplicationId applicationId, JobType jobType, long buildId, String testLog) {

    }

    @Override
    public String getConvergenceLog(ApplicationId applicationId, JobType jobType, long buildId) {
        return "SUCCESS";
    }

    @Override
    public void setConvergenceLog(ApplicationId applicationId, JobType jobType, long buildId, String convergenceLog) {

    }

    @Override
    public PrepareResponse getPrepareResponse(ApplicationId applicationId, JobType jobType, long buildId) {
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.message = "foo";
        prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(),
                Collections.emptyList());
        prepareResponse.tenant = new TenantId("tenant");
        return prepareResponse;    }

    @Override
    public void setPrepareResponse(ApplicationId applicationId, JobType jobType, long buildId, PrepareResponse prepareResponse) {

    }

    @Override
    public void deleteTestData(ApplicationId applicationId, JobType jobType, long buildId) {

    }
}
