package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.math.BigDecimal;

public record PriceInformation(BigDecimal listPrice, BigDecimal volumeDiscount) {

}
