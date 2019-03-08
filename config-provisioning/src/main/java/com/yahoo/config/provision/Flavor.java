// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;

/**
 * A host flavor (type).
 *
 * @author bratseth
 */
public interface Flavor {

    /** @return the unique identity of this flavor */
    String flavorName();

    /**
     * Returns the canonical name of this flavor - which is the name which should be used as an interface to users.
     * The canonical name of this flavor is:
     * <ul>
     *   <li>If it replaces one flavor, the canonical name of the flavor it replaces
     *   <li>If it replaces multiple or no flavors - itself
     * </ul>
     *
     * The logic is that we can use this to capture the gritty details of configurations in exact flavor names
     * but also encourage users to refer to them by a common name by letting such flavor variants declare that they
     * replace the canonical name we want. However, if a node replaces multiple names, we have no basis for choosing one
     * of them as the canonical, so we return the current as canonical.
     */
    default String canonicalName() {
        return replaces().size() != 1 ? flavorName() : replaces().get(0).canonicalName();
    }

    /** @return the cost associated with usage of this flavor */
    int cost();

    /**
     * A stock flavor is any flavor we expect more of in the future.
     * Stock flavors are assigned to applications by cost priority.
     *
     * Non-stock flavors are used for nodes for which a fixed amount has already been added
     * to the system for some historical reason. These nodes are assigned to applications
     * when available by exact match and ignoring cost.
     */
    boolean isStock();

    /** Returns whether the flavor is retired (should no longer be allocated) */
    boolean isRetired();

    /**
     * Returns whether this flavor satisfies the requested flavor, either directly
     * (by being the same), or by directly or indirectly replacing it
     */
    default boolean satisfies(Flavor flavor) {
        if (equals(flavor)) {
            return true;
        }
        if (isRetired()) {
            return false;
        }
        for (Flavor replaces : replaces())
            if (replaces.satisfies(flavor))
                return true;
        return false;
    }

    Cpu cpu();

    Memory memory();

    Disk disk();

    Bandwidth bandwidth();

    Environment environment();

    /** The flavors this (directly) replaces. */
    List<Flavor> replaces();


    interface Disk {

        /** @return Disk size in GB in base 10 (1GB = 10^9 bytes) */
        double sizeInBase10Gb();

        /** @return Disk size in GB in base 2, also known as GiB (1GiB = 2^30 bytes), rounded to nearest integer value */
        default double sizeInBase2Gb() {
            return Math.round(sizeInBase10Gb() / Math.pow(1.024, 3));
        }

        boolean isFast();
    }

    interface Memory {
        double sizeInGb();
    }

    interface Cpu {
        double cores();
    }

    interface Bandwidth {
        double mbits();
    }

    enum Environment {
        BARE_METAL,
        VIRTUAL_MACHINE,
        DOCKER_CONTAINER
    }
}
