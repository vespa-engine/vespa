// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All the available node flavors.
 *
 * @author freva
 */
public interface NodeFlavors {

    /** Returns list of all available flavors in the system */
    List<Flavor> getFlavors();

    /** Returns a flavor by name, or empty if there is no flavor with this name. */
    Optional<Flavor> getFlavor(String name);

    /** Returns the flavor with the given name or throws an IllegalArgumentException if it does not exist */
    default Flavor getFlavorOrThrow(String flavorName) {
        return getFlavor(flavorName).orElseThrow(() -> new IllegalArgumentException("Unknown flavor '" + flavorName +
                "'. Flavors are " + canonicalFlavorNames()));
    }

    private List<String> canonicalFlavorNames() {
        return getFlavors().stream().map(Flavor::canonicalName).distinct().sorted().collect(Collectors.toList());
    }
}
