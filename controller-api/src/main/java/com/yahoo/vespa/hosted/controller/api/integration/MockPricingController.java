// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PriceInformation;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingController;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;

import java.math.BigDecimal;
import java.util.List;

import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel.BASIC;

public class MockPricingController implements PricingController {

    @Override
    public PriceInformation price(List<ClusterResources> clusterResources, PricingInfo pricingInfo, Plan plan) {
        BigDecimal listPrice = BigDecimal.valueOf(clusterResources.stream()
                                                          .mapToDouble(resources -> resources.nodes() *
                                                                  (resources.nodeResources().vcpu() * 1000 +
                                                                          resources.nodeResources().memoryGb() * 100 +
                                                                          resources.nodeResources().diskGb() * 10))
                                                          .sum());

        BigDecimal supportLevelCost = pricingInfo.supportLevel() == BASIC ? new BigDecimal("-160.00") : new BigDecimal("800.00");
        BigDecimal listPriceWithSupport = listPrice.add(supportLevelCost);
        BigDecimal enclaveDiscount = pricingInfo.enclave() ? new BigDecimal("-15.1234") : BigDecimal.ZERO;
        BigDecimal volumeDiscount = new BigDecimal("-5.64315634");
        BigDecimal committedAmountDiscount = new BigDecimal("-1.23");
        BigDecimal totalAmount = listPrice.add(supportLevelCost).add(enclaveDiscount).add(volumeDiscount).add(committedAmountDiscount);
        return new PriceInformation(listPriceWithSupport, volumeDiscount, committedAmountDiscount, enclaveDiscount, totalAmount);
    }

}
