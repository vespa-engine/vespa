// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;

import static com.yahoo.config.provision.NodeResources.Architecture.*;

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
        b.addFlavor("default", 2., 16., 400, 10, Flavor.Type.BARE_METAL, x86_64);
        b.addFlavor("medium-disk", 6., 12., 56, 10, Flavor.Type.BARE_METAL, x86_64);
        b.addFlavor("large", 4., 32., 1600, 20, Flavor.Type.BARE_METAL, x86_64);
        b.addFlavor("docker", 0.2, 0.5, 100, 1, Flavor.Type.DOCKER_CONTAINER, x86_64);
        b.addFlavor("d-2-8-100", 2, 8, 100, 1, Flavor.Type.DOCKER_CONTAINER, x86_64);
        b.addFlavor("v-4-8-100", 4.0, 8.0, 100, 5, Flavor.Type.VIRTUAL_MACHINE, x86_64);
        b.addFlavor("large-variant", 64, 128, 2000, 15, Flavor.Type.BARE_METAL, x86_64);
        b.addFlavor("expensive", 6, 12, 500, 5, Flavor.Type.BARE_METAL, x86_64);
        b.addFlavor("arm64", 4, 32., 1600, 20, Flavor.Type.BARE_METAL, arm64);

        return b.build();
    }

}
