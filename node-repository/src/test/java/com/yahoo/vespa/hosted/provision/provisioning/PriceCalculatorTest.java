package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.yahoo.vespa.hosted.provision.provisioning.PriceCalculator.PricingInfo;

/**
 * @author bratseth
 */
public class PriceCalculatorTest {

    @Test
    public void testPrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(6, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3,
                                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                              NodeResources.Architecture.x86_64,
                                                              new NodeResources.GpuResources(2, 40)));
        assertEquals(38.95, calculator.price(List.of(c1, c2), PricingInfo.empty()), 0.01);
    }

    @Test
    public void testEnclavePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(6, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3,
                                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                              NodeResources.Architecture.x86_64,
                                                              new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PricingInfo(true, PricingInfo.SupportLevel.ENTERPRISE, 0);
        assertEquals(35.80, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testMinimalEnclavePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3));
        var pricingInfo = new PriceCalculator.PricingInfo(true, PricingInfo.SupportLevel.ENTERPRISE, 0);
        assertEquals(13.89, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testCommittedSpendPriceUsingMore() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(6, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3,
                                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                              NodeResources.Architecture.x86_64,
                                                              new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PricingInfo(true, PricingInfo.SupportLevel.ENTERPRISE, 30);
        assertEquals(33.40, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testCommittedSpendPriceUsingLess() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(6, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3,
                                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                              NodeResources.Architecture.x86_64,
                                                              new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PricingInfo(true, PricingInfo.SupportLevel.ENTERPRISE, 40);
        assertEquals(40.0, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testLargeVolumePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(29, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(31, 1, new NodeResources(10, 100, 1000, 0.3,
                                                               NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                               NodeResources.Architecture.x86_64,
                                                               new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PriceCalculator.PricingInfo(false, PricingInfo.SupportLevel.STANDARD, 40);
        // System.out.println("Yearly cost:  " + 24*365*(c1.cost() + c2.cost()));
        // System.out.println("Yearly price: " + 24*365*calculator.price(List.of(c1, c2), pricingInfo));
        assertEquals(153.60, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testLargeVolumeEnclavePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(29, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(31, 1, new NodeResources(10, 100, 1000, 0.3,
                                                               NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                               NodeResources.Architecture.x86_64,
                                                               new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PriceCalculator.PricingInfo(true, PricingInfo.SupportLevel.STANDARD, 40);
        // System.out.println("Yearly cost:  " + 24*365*(c1.cost() + c2.cost()));
        // System.out.println("Yearly price: " + 24*365*calculator.price(List.of(c1, c2), pricingInfo));
        assertEquals(40.21, calculator.price(List.of(c1, c2), pricingInfo), 0.01);
    }

}
