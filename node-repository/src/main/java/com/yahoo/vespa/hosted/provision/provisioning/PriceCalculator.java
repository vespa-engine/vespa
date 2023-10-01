package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterResources;

import java.util.List;
import static java.lang.Math.min;

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
    private final double priceGivingMaxDiscount = 200; // 70 nodes with resources 16, 64, 800
    private final double minimalMonthlyEnclaveCost = 10_000;

    /** Calculates the price of this application in USD per hour. */
    public double price(List<ClusterResources> clusters, PricingInfo pricingInfo) {
        double cost = clusters.stream()
                              .mapToDouble(cluster -> cluster.cost())
                              .sum();
        return price(cost, pricingInfo.enclave, pricingInfo.supportLevel);
    }

    private double price(double cost, boolean enclave, PricingInfo.SupportLevel supportLevel) {
        double price = supportLevelMultiplier(supportLevel) * cost;

        price = enclave ? price * (1 - enclaveDiscount) : price;

        var volumeDiscount = ( enclave ? maxEnclaveDiscount: maxDiscount ) * min(1, price / priceGivingMaxDiscount);
        price = price * (1 - volumeDiscount);

        if (enclave && price * 24 * 30 < minimalMonthlyEnclaveCost)
            price = minimalMonthlyEnclaveCost / (24 * 30);

        return price;
    }

    private double supportLevelMultiplier(PricingInfo.SupportLevel supportLevel) {
        return switch (supportLevel) {
            case STANDARD ->   2.8/3;
            case COMMERCIAL -> 4.0/3;
            case ENTERPRISE -> 5.0/3;
        };
    }

    public record PricingInfo(boolean enclave, SupportLevel supportLevel) {

        public enum SupportLevel { STANDARD, COMMERCIAL, ENTERPRISE }

        public static PricingInfo empty() { return new PricingInfo(false, SupportLevel.COMMERCIAL); }

    }

}
