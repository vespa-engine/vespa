// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;

import java.util.Comparator;

/**
 * Resource comparator
 *
 * @author bratseth
 */
public class NodeResourceComparator {

    private static final MemoryDiskCpu memoryDiskCpuComparator = new MemoryDiskCpu();

    /** Returns the default ordering */
    public static Comparator<NodeResources> defaultOrder() { return memoryDiskCpuOrder(); }

    /** Returns a comparator comparing by memory, disk, vcpu */
    public static Comparator<NodeResources> memoryDiskCpuOrder() { return memoryDiskCpuComparator; }

    private static class MemoryDiskCpu implements Comparator<NodeResources> {

        @Override
        public int compare(NodeResources a, NodeResources b) {
            if (a.memoryGb() > b.memoryGb()) return 1;
            if (a.memoryGb() < b.memoryGb()) return -1;
            if (a.diskGb() > b.diskGb()) return 1;
            if (a.diskGb() < b.diskGb()) return -1;
            if (a.vcpu() > b.vcpu()) return 1;
            if (a.vcpu() < b.vcpu()) return -1;

            int storageTypeComparison = NodeResources.StorageType.compare(a.storageType(), b.storageType());
            if (storageTypeComparison != 0) return storageTypeComparison;

            return compare(a.diskSpeed(), b.diskSpeed());
        }

        private int compare(NodeResources.DiskSpeed a, NodeResources.DiskSpeed b) {
            if (a == b) return 0;
            if (a == NodeResources.DiskSpeed.slow) return -1;
            if (b == NodeResources.DiskSpeed.slow) return 1;
            return 0;
        }

    }

}
