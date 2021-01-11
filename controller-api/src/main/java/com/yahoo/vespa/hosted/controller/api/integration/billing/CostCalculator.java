// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostInfo;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceUsage;

/**
 * @author ogronnesby
 */
public interface CostCalculator {

    /** Calculate the cost for the given usage */
    CostInfo calculate(ResourceUsage usage);

    /** Estimate the cost for the given resources */
    double calculate(NodeResources resources);

}
