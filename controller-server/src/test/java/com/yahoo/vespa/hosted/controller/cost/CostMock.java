// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.api.integration.cost.ApplicationCost;
import com.yahoo.vespa.hosted.controller.api.integration.cost.Backend;
import com.yahoo.vespa.hosted.controller.api.integration.cost.Cost;
import com.yahoo.vespa.hosted.controller.common.NotFoundCheckedException;

import java.util.List;

/**
 * @author mpolden
 */
public class CostMock implements Cost {

    private final Backend backend;

    public CostMock(Backend backend) {
        this.backend = backend;
    }

    @Override
    public List<ApplicationCost> getCPUAnalysis(int nofApplications) {
        return null;
    }

    @Override
    public String getCsvForLocalAnalysis() {
        return null;
    }

    @Override
    public List<ApplicationCost> getApplicationCost() {
        return backend.getApplicationCost();
    }

    @Override
    public ApplicationCost getApplicationCost(Environment env, RegionName region, ApplicationId app) throws NotFoundCheckedException {
        return backend.getApplicationCost(env, region, app);
    }
}
