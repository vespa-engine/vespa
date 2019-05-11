// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import java.util.Comparator;

/**
 * Resource comparator
 *
 * @author bratseth
 */
public class ResourceCapacityComparator {

    private static final MemoryDiskCpu memoryDiskCpuComparator = new MemoryDiskCpu();

    /** Returns the default ordering */
    public static Comparator<ResourceCapacity> defaultOrder() { return memoryDiskCpuOrder(); }

    /** Returns a comparator comparing by memory, disk, vcpu */
    public static Comparator<ResourceCapacity> memoryDiskCpuOrder() { return memoryDiskCpuComparator; }

    private static class MemoryDiskCpu implements Comparator<ResourceCapacity> {

        @Override
        public int compare(ResourceCapacity a, ResourceCapacity b) {
            if (a.memoryGb() > b.memoryGb()) return 1;
            if (a.memoryGb() < b.memoryGb()) return -1;
            if (a.diskGb() > b.diskGb()) return 1;
            if (a.diskGb() < b.diskGb()) return -1;
            if (a.vcpu() > b.vcpu()) return 1;
            if (a.vcpu() < b.vcpu()) return -1;
            return 0;
        }

    }

}
