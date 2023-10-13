// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.ApplicationResources;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PriceInformation;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.Prices;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingController;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;

import java.math.BigDecimal;
import java.util.List;

import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel.BASIC;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;

public class MockPricingController implements PricingController {

    // TODO: Remove when not in use anymore
    @Override
    public PriceInformation price(List<ClusterResources> clusterResources, PricingInfo pricingInfo, Plan plan) {
        BigDecimal listPrice = valueOf(clusterResources.stream()
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
        return new PriceInformation("default", listPriceWithSupport, volumeDiscount, committedAmountDiscount, enclaveDiscount, totalAmount);
    }

    @Override
    public Prices priceForApplications(List<ApplicationResources> applicationResources, PricingInfo pricingInfo, Plan plan) {
        ApplicationResources resources = applicationResources.get(0);
        BigDecimal listPrice = resources.vcpu().multiply(valueOf(1000))
                .add(resources.memoryGb().multiply(valueOf(100)))
                .add(resources.diskGb().multiply(valueOf(10)))
                .add(resources.enclaveVcpu().multiply(valueOf(1000))
                .add(resources.enclaveMemoryGb().multiply(valueOf(100)))
                .add(resources.enclaveDiskGb().multiply(valueOf(10))));

        BigDecimal supportLevelCost = pricingInfo.supportLevel() == BASIC ? new BigDecimal("-160.00") : new BigDecimal("800.00");
        BigDecimal listPriceWithSupport = listPrice.add(supportLevelCost);
        BigDecimal enclaveDiscount = (resources.enclaveVcpu().compareTo(ZERO) > 0) ? new BigDecimal("-15.1234") : BigDecimal.ZERO;
        BigDecimal volumeDiscount = new BigDecimal("-5.64315634");
        BigDecimal committedAmountDiscount = new BigDecimal("-1.23");
        BigDecimal totalAmount = listPrice.add(supportLevelCost).add(enclaveDiscount).add(volumeDiscount).add(committedAmountDiscount);

        var appPrice = new PriceInformation("app1", listPriceWithSupport, volumeDiscount, committedAmountDiscount, enclaveDiscount, totalAmount);
        var totalPrice = new PriceInformation("total", ZERO, ZERO, committedAmountDiscount, enclaveDiscount, totalAmount);

        return new Prices(List.of(appPrice), totalPrice);
    }

}
