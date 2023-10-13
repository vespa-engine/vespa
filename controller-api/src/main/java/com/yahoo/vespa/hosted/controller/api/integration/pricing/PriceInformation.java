// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.math.BigDecimal;
import java.util.List;

import static java.math.BigDecimal.ZERO;

public record PriceInformation(String applicationName, BigDecimal listPriceWithSupport, BigDecimal volumeDiscount,
                               BigDecimal committedAmountDiscount, BigDecimal enclaveDiscount, BigDecimal totalAmount) {

    public static PriceInformation empty() { return new PriceInformation("default", ZERO, ZERO, ZERO, ZERO, ZERO); }

    public static PriceInformation sum(List<PriceInformation> priceInformationList) {
        var result = PriceInformation.empty();
        for (var prices : priceInformationList)
            result = result.add(prices);
        return result;
    }

    public PriceInformation add(PriceInformation priceInformation) {
        return new PriceInformation("accumulated",
                                    this.listPriceWithSupport().add(priceInformation.listPriceWithSupport()),
                                    this.volumeDiscount().add(priceInformation.volumeDiscount()),
                                    this.committedAmountDiscount().add(priceInformation.committedAmountDiscount()),
                                    this.enclaveDiscount().add(priceInformation.enclaveDiscount()),
                                    this.totalAmount().add(priceInformation.totalAmount()));
    }

}
