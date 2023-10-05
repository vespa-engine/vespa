// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;

import java.util.List;

/**
 * A service that calculates price information based on cluster resources, plan, service level etc.
 *
 * @author hmusum
 */
public interface PricingController {

    PriceInformation price(List<ClusterResources> clusterResources, PricingInfo pricingInfo, Plan plan);

}
