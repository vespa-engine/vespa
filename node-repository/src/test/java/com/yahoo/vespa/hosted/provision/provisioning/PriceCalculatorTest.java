package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(38.95, calculator.cost(List.of(c1, c2), PriceCalculator.PricingInfo.empty()), 0.01);
    }

    @Test
    public void testEnclavePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(6, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3,
                                                              NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                              NodeResources.Architecture.x86_64,
                                                              new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PriceCalculator.PricingInfo(true, PriceCalculator.PricingInfo.SupportLevel.ENTERPRISE);
        assertEquals(36.08, calculator.cost(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testEnclavePrice2() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(29, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(31, 1, new NodeResources(10, 100, 1000, 0.3,
                                                                NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                                NodeResources.Architecture.x86_64,
                                                                new NodeResources.GpuResources(2, 40)));
        var pricingInfo = new PriceCalculator.PricingInfo(true, PriceCalculator.PricingInfo.SupportLevel.STANDARD);
        System.out.println("Cost:  " + 24*365*(c1.cost() + c2.cost()));
        System.out.println("Price: " + 24*365*calculator.cost(List.of(c1, c2), pricingInfo));
        assertEquals(51.07, calculator.cost(List.of(c1, c2), pricingInfo), 0.01);
    }

    @Test
    public void testMinimalEnclavePrice() {
        PriceCalculator calculator = new PriceCalculator();
        var c1 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3));
        var c2 = new ClusterResources(2, 1, new NodeResources(10, 100, 1000, 0.3));
        var pricingInfo = new PriceCalculator.PricingInfo(true, PriceCalculator.PricingInfo.SupportLevel.ENTERPRISE);
        assertEquals(13.89, calculator.cost(List.of(c1, c2), pricingInfo), 0.01);
    }
}
