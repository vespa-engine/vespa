// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PriceInformation;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingController;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;

import java.math.BigDecimal;
import java.util.List;

public class MockPricingController implements PricingController {

    @Override
    public PriceInformation price(List<ClusterResources> clusterResources, PricingInfo pricingInfo, Plan plan) {
        BigDecimal listPrice = BigDecimal.valueOf(clusterResources.stream()
                                                          .mapToDouble(resources -> resources.nodes() *
                                                                  (resources.nodeResources().vcpu() * 1000 +
                                                                          resources.nodeResources().memoryGb() * 100 +
                                                                          resources.nodeResources().diskGb() * 10))
                                                          .sum());
        BigDecimal volumeDiscount = new BigDecimal("-5.64315634");
        BigDecimal committedAmountDiscount = new BigDecimal("0.00");
        BigDecimal enclaveDiscount = pricingInfo.enclave() ? new BigDecimal("-15.1234") : BigDecimal.ZERO;
        BigDecimal totalAmount = listPrice.add(volumeDiscount);
        return new PriceInformation(listPrice, volumeDiscount, committedAmountDiscount, enclaveDiscount, totalAmount);
    }

}
