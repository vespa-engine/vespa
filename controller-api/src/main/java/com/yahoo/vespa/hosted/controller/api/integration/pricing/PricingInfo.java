// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.pricing;

public record PricingInfo(boolean enclave, SupportLevel supportLevel, double committedHourlyAmount) {

    public enum SupportLevel { BASIC, COMMERCIAL, ENTERPRISE }

    public static PricingInfo empty() { return new PricingInfo(false, SupportLevel.COMMERCIAL, 0); }

}
