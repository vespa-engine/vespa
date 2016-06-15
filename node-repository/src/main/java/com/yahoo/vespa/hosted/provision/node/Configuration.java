// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import java.util.Objects;

/**
 * The hardware configuration of a node
 *
 * @author bratseth
 */
public class Configuration {

    private final Flavor flavor;

    public Configuration(Flavor flavor) {
        Objects.requireNonNull(flavor, "A node configuration must have a flavor");
        this.flavor = flavor;
    }

    /** Returns the name of this hardware configuration */
    public Flavor flavor() { return flavor; }

    /** Returns a configuration with the flavor set to the given value */
    public Configuration setFlavor(Flavor flavor) { return new Configuration(flavor); }

    @Override
    public String toString() {
        return flavor.toString();
    }

}
