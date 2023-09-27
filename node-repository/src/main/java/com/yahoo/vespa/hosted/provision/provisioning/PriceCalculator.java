package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterResources;

import java.util.List;

/**
 * Calculates the cost of an application.
 * Cost depends on volume, so all clusters of the application must be given at the same time.
 *
 * @author bratseth
 */
public class PriceCalculator {

    private final double maxDiscount = 0.5;
    private final double enclaveDiscount = 0.2;
    private final double maxEnclaveDiscount = 0.80;
    private final double costGivingMaxDiscount = 200; // 70 nodes with resources 16, 64, 800
    private final double minimalMonthlyEnclaveCost = 10_000;

    /** Calculates the price of this application in USD per hour. */
    public double cost(List<ClusterResources> clusters, PricingInfo pricingInfo) {
        double cost = clusters.stream()
                              .mapToDouble(cluster -> cluster.cost())
                              .sum();
        cost = supportLevelMultiplier(pricingInfo.supportLevel) * cost;

        cost = pricingInfo.enclave ? cost * (1 - enclaveDiscount) : cost;

        double volumeDiscount = pricingInfo.enclave
                                ? maxEnclaveDiscount * Math.min(1, cost / costGivingMaxDiscount)
                                : maxDiscount * Math.min(1, cost / costGivingMaxDiscount);
        double price = cost * (1 - volumeDiscount);

        if (pricingInfo.enclave && price * 24 * 30 < minimalMonthlyEnclaveCost)
            price = minimalMonthlyEnclaveCost / (24 * 30);

        return price;
    }

    private double supportLevelMultiplier(PricingInfo.SupportLevel supportLevel) {
        return switch (supportLevel) {
            case STANDARD -> 0.8;
            case COMMERCIAL -> 1.0;
            case ENTERPRISE -> 1.4;
        };
    }

    public record PricingInfo(boolean enclave, SupportLevel supportLevel) {

        public enum SupportLevel { STANDARD, COMMERCIAL, ENTERPRISE }

        public static PricingInfo empty() { return new PricingInfo(false, SupportLevel.COMMERCIAL); }

    }

}
