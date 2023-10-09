package com.yahoo.vespa.hosted.controller.api.integration.pricing;

public record PricingInfo(boolean enclave, SupportLevel supportLevel, double committedHourlyAmount) {

    public enum SupportLevel { BASIC, COMMERCIAL, ENTERPRISE }

    public static PricingInfo empty() { return new PricingInfo(false, SupportLevel.COMMERCIAL, 0); }

}