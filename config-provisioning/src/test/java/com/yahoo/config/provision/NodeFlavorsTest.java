// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provisioning.FlavorsConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NodeFlavorsTest {

    private static final double delta = 0.00001;

    @Test
    public void testConfigParsing() {
        FlavorsConfig.Builder builder = new FlavorsConfig.Builder();
        List<FlavorsConfig.Flavor.Builder> flavorBuilderList = new ArrayList<>();
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.name("strawberry").cost(2);
            flavorBuilderList.add(flavorBuilder);
        }
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.minCpuCores(10);
            flavorBuilder.cpuSpeedup(1.3);
            flavorBuilder.name("banana").cost(3);
            flavorBuilderList.add(flavorBuilder);
        }
        builder.flavor(flavorBuilderList);
        FlavorsConfig config = new FlavorsConfig(builder);
        NodeFlavors nodeFlavors = new NodeFlavors(config);
        Flavor banana = nodeFlavors.getFlavor("banana").get();
        assertEquals(3, banana.cost());
        assertEquals(10, banana.getMinCpuCores(), delta);
        assertEquals("10 * 1.3", 13, banana.resources().vcpu(), delta);
    }

}
