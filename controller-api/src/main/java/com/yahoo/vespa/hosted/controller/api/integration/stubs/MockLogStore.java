// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.configserverbindings.ConfigChangeActions;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Collections;

/**
 * @author freva
 */
public class MockLogStore implements LogStore {

    @Override
    public String getTestLog(RunId id) {
        return "SUCCESS";
    }

    @Override
    public void setTestLog(RunId id, String testLog) {

    }

    @Override
    public String getConvergenceLog(RunId id) {
        return "SUCCESS";
    }

    @Override
    public void setConvergenceLog(RunId id, String convergenceLog) {

    }

    @Override
    public PrepareResponse getPrepareResponse(RunId id) {
        PrepareResponse prepareResponse = new PrepareResponse();
        prepareResponse.message = "foo";
        prepareResponse.configChangeActions = new ConfigChangeActions(Collections.emptyList(),
                                                                      Collections.emptyList());
        prepareResponse.tenant = new TenantId("tenant");
        return prepareResponse;
    }

    @Override
    public void setPrepareResponse(RunId id, PrepareResponse prepareResponse) {

    }

    @Override
    public void deleteTestData(RunId id) {

    }

}
