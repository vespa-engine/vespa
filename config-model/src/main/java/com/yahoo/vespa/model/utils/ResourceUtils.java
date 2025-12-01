// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.Host;

/**
 * Very simple utility functionality for adjusting or converting resource values.
 *
 * @author vekterli
 */
public class ResourceUtils {

    public final static long MiB = 1024 * 1024;
    public final static long GiB = MiB * 1024;
    public final static long GB  = 1_000_000_000;

    /**
     * Returns the amount of actually usable memory (in GiB) once the fixed
     * host overhead has been accounted for.
     *
     * @param memoryGiB Non-adjusted host memory in GiB
     * @return memory in GiB after host overhead has been subtracted
     */
    public static double usableMemoryGb(double memoryGiB) {
        // Assume, for simplicity, that we can't have negative amounts of RAM.
        // TODO Gb or GiB...? Units need to match since these are floats of how
        //  many GiBs (or GBs!) there are, not the number of bytes...!
        return Math.max(memoryGiB - Host.memoryOverheadGb, 0);
    }

    /**
     * Returns {@link #usableMemoryGb(double)} from {@link NodeResources#memoryGiB()}.
     */
    public static double usableMemoryGb(NodeResources resources) {
        return usableMemoryGb(resources.memoryGiB());
    }

}
