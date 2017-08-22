// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.common.NotFoundCheckedException;

import java.util.List;

/**
 * Interface for retrieving cost data directly or indirectly from yamas and
 * the noderepository.
 *
 *
 * @author smorgrav
 */
public interface Backend {
    List<ApplicationCost> getApplicationCost();
    ApplicationCost getApplicationCost(Environment env, RegionName region, ApplicationId appId) throws NotFoundCheckedException;
}
