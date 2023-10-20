// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

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

public class MockPricingController implements PricingController {

    private static final BigDecimal cpuCost = new BigDecimal("1.00");
    private static final BigDecimal memoryCost = new BigDecimal("0.10");
    private static final BigDecimal diskCost = new BigDecimal("0.005");

    @Override
    public Prices priceForApplications(List<ApplicationResources> applicationResources, PricingInfo pricingInfo, Plan plan) {
        List<PriceInformation> appPrices = applicationResources.stream()
                .map(resources -> {
                    BigDecimal listPrice = resources.vcpu().multiply(cpuCost)
                            .add(resources.memoryGb().multiply(memoryCost))
                            .add(resources.diskGb().multiply(diskCost))
                            .add(resources.enclaveVcpu().multiply(cpuCost))
                            .add(resources.enclaveMemoryGb().multiply(memoryCost))
                            .add(resources.enclaveDiskGb().multiply(diskCost));

                    BigDecimal supportLevelCost = pricingInfo.supportLevel() == BASIC ? new BigDecimal("-1.00") : new BigDecimal("8.00");
                    BigDecimal listPriceWithSupport = listPrice.add(supportLevelCost);
                    BigDecimal enclaveDiscount = isEnclave(resources) ? new BigDecimal("-0.15") : BigDecimal.ZERO;
                    BigDecimal volumeDiscount = new BigDecimal("-0.1");
                    BigDecimal appTotalAmount = listPrice.add(supportLevelCost).add(enclaveDiscount).add(volumeDiscount);

                    return new PriceInformation(listPriceWithSupport,
                            volumeDiscount,
                            ZERO,
                            enclaveDiscount,
                            appTotalAmount);
                })
                .toList();

        PriceInformation sum = PriceInformation.sum(appPrices);
        System.out.println(pricingInfo.committedHourlyAmount());
        var committedAmountDiscount = pricingInfo.committedHourlyAmount().compareTo(ZERO) > 0 ? new BigDecimal("-0.2") : ZERO;
        var totalAmount = sum.totalAmount().add(committedAmountDiscount);
        var enclave = ZERO;
        if (applicationResources.stream().anyMatch(ApplicationResources::enclave) && totalAmount.compareTo(new BigDecimal("14.00")) < 0)
            enclave = new BigDecimal("14.00").subtract(totalAmount);
        var totalPrice = new PriceInformation(ZERO, ZERO, committedAmountDiscount, enclave, totalAmount);

        return new Prices(appPrices, totalPrice);
    }

    private static boolean isEnclave(ApplicationResources resources) {
        return resources.enclaveVcpu().compareTo(ZERO) > 0;
    }

}
