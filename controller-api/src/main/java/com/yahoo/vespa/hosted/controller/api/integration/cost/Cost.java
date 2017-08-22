// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.common.NotFoundCheckedException;

import java.util.List;

/**
 * Cost domain model declaration
 *
 * @author smorgrav
 */
public interface Cost {

    /**
     * Calculate a list of the applications that is wasting most
     * in absolute terms. To improve utilization, it should make
     * sense to focus on this list.
     *
     * @return An ordered set of applications with the highest potential for
     * improved CPU utilization across all environments and regions.
     */
    List<ApplicationCost> getCPUAnalysis(int nofApplications);

    /**
     * Collect all information and format it as a Cvs blob for download.
     *
     * @return A String with comma separated values. Can be big!
     */
    String getCsvForLocalAnalysis();

    /**
     * Get application costs for all applications across all regions and environments
     *
     * @return A list of applications in given zone
     */
    List<ApplicationCost> getApplicationCost();

    /**
     * Get application costs for a given application instance in a given zone.
     *
     * @param env Environment like test, dev, perf, staging or prod
     * @param region Region name like us-east-1
     * @param app ApplicationId like tenant:application:instance
     *
     * @return A list of applications in given zone
     */
    ApplicationCost getApplicationCost(Environment env, RegionName region, ApplicationId app)
        throws NotFoundCheckedException;
}
