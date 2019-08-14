// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;

/**
 * A mock repository prepopulated with flavors, to avoid having config.
 * Instantiated by DI from application package above.
 */
public class MockNodeFlavors extends NodeFlavors {

    public MockNodeFlavors() {
        super(createConfig());
    }

    private static FlavorsConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 16., 400, Flavor.Type.BARE_METAL);
        b.addFlavor("medium-disk", 6., 12., 56, Flavor.Type.BARE_METAL);
        b.addFlavor("large", 4., 32., 1600, Flavor.Type.BARE_METAL);
        b.addFlavor("docker", 0.2, 0.5, 100, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2-8-100", 2, 8, 100, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("v-4-8-100", 4.0, 8.0, 100, Flavor.Type.VIRTUAL_MACHINE);
        FlavorsConfig.Flavor.Builder largeVariant = b.addFlavor("large-variant", 64, 128, 2000, Flavor.Type.BARE_METAL);
        b.addReplaces("large", largeVariant);
        FlavorsConfig.Flavor.Builder expensiveFlavor = b.addFlavor("expensive", 6, 12, 500, Flavor.Type.BARE_METAL);
        b.addReplaces("default", expensiveFlavor);
        b.addCost(200, expensiveFlavor);

        return b.build();
    }

}
