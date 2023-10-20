// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;

public record PricingInfo(SupportLevel supportLevel, BigDecimal committedHourlyAmount) {

    public enum SupportLevel { BASIC, COMMERCIAL, ENTERPRISE }

    public static PricingInfo empty() { return new PricingInfo(SupportLevel.COMMERCIAL, ZERO); }

}
