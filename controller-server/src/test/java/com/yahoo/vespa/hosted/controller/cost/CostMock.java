// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.cost;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.api.integration.cost.Cost;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostApplication;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostClusterInfo;
import com.yahoo.vespa.hosted.controller.api.integration.cost.CostResources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mpolden
 */
public class CostMock extends AbstractComponent implements Cost  {

    private final Map<ApplicationId, CostApplication> applicationCost = new HashMap<>();

    @Override
    public List<CostApplication> getApplicationCost() {
        return new ArrayList<>(applicationCost.values());
    }

    @Override
    public Map<Zone, List<ApplicationId>> getApplications() {
        return null;
    }

    @Override
    public Map<String, CostClusterInfo> getClusterInfo(Zone zone, ApplicationId app) {
        return null;
    }

    @Override
    public Map<String, CostResources> getClusterUtilization(Zone zone, ApplicationId app) {
        return null;
    }

    public void setApplicationCost(ApplicationId application, CostApplication cost) {
        applicationCost.put(application, cost);
    }
}
