package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterResources;

import java.util.List;
import static java.lang.Math.min;
import static java.lang.Math.max;

/**
 * Calculates the cost of an application.
 * Cost depends on volume, so all clusters of the application must be given at the same time.
 *
 * @author bratseth
 */
public class PriceCalculator {

    private final double enclaveDiscount = 0.2;

    private final double maxVolumeDiscount = 0.5;
    private final double maxVolumeDiscountEnclave = 0.83;
    private final double priceGivingMaxVolumeDiscount = 200; // 70 nodes with resources 16, 64, 800

    private final double committedDiscount = 0.15;
    private final double committedDiscountEnclave = 0.08;

    private final double minimalMonthlyEnclavePrice = 10_000; // 13.89 per hour

    /** Calculates the price of this application in USD per hour. */
    public double price(List<ClusterResources> clusters, PricingInfo pricingInfo) {
        double cost = clusters.stream()
                              .mapToDouble(cluster -> cluster.cost())
                              .sum();
        return price(cost, pricingInfo.enclave, pricingInfo.supportLevel, pricingInfo.committedHourlyAmount);
    }

    private double price(double cost, boolean enclave, PricingInfo.SupportLevel supportLevel, double committedAmount) {
        double price = supportLevelMultiplier(supportLevel) * cost;

        price = enclave ? price * (1 - enclaveDiscount) : price;

        var volumeDiscount = (enclave ? maxVolumeDiscountEnclave : maxVolumeDiscount)
                             * min(1, price / priceGivingMaxVolumeDiscount);
        price = price * (1 - volumeDiscount);

        price = min(price, committedAmount) * (1 - ( enclave ? committedDiscountEnclave : committedDiscount))
                + max(0, price - committedAmount);
        price = max(price, committedAmount);

        if (enclave && price * 24 * 30 < minimalMonthlyEnclavePrice)
            price = minimalMonthlyEnclavePrice / (24 * 30);

        return price;
    }

    private double supportLevelMultiplier(PricingInfo.SupportLevel supportLevel) {
        return switch (supportLevel) {
            case STANDARD ->   2.8/3;
            case COMMERCIAL -> 4.0/3;
            case ENTERPRISE -> 5.0/3;
        };
    }

    public record PricingInfo(boolean enclave, SupportLevel supportLevel, double committedHourlyAmount) {

        public enum SupportLevel { STANDARD, COMMERCIAL, ENTERPRISE }

        public static PricingInfo empty() { return new PricingInfo(false, SupportLevel.COMMERCIAL, 0); }

    }

}
