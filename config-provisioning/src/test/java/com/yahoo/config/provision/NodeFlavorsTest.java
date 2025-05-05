// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provisioning.FlavorsConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeFlavorsTest {

    private static final double delta = 0.00001;

    @Test
    void testConfigParsing() {
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
            flavorBuilder.name("bare-metal-banana").cost(3);
            flavorBuilder.environment("BARE_METAL");
            flavorBuilderList.add(flavorBuilder);
        }
        {
            FlavorsConfig.Flavor.Builder flavorBuilder = new FlavorsConfig.Flavor.Builder();
            flavorBuilder.minCpuCores(10);
            flavorBuilder.cpuSpeedup(1.3);
            flavorBuilder.name("docker-banana").cost(3);
            flavorBuilder.environment("DOCKER_CONTAINER");
            flavorBuilderList.add(flavorBuilder);
        }
        builder.flavor(flavorBuilderList);
        FlavorsConfig config = new FlavorsConfig(builder);
        NodeFlavors nodeFlavors = new NodeFlavors(config);

        Flavor bareMetalBanana = nodeFlavors.getFlavor("bare-metal-banana").get();
        assertEquals(3, bareMetalBanana.cost());
        assertEquals(13, bareMetalBanana.resources().vcpu(), delta, "Bare metal is adjusted by cpu speed: 10 * 1.3");

        Flavor dockerBanana = nodeFlavors.getFlavor("docker-banana").get();
        assertEquals(3, dockerBanana.cost());
        assertEquals(10, dockerBanana.resources().vcpu(), delta, "Docker containers are not adjusted by cpu speed");
    }

}
