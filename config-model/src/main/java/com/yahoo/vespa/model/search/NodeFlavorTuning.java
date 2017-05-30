package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.config.search.core.ProtonConfig;

import static java.lang.Long.min;
import static java.lang.Integer.max;

/**
 * Tuning of proton config for a search node based on the node flavor of that node.
 *
 * @author geirst
 */
public class NodeFlavorTuning implements ProtonConfig.Producer {

    static long MB = 1024 * 1024;
    static long GB = MB * 1024;
    private final Flavor nodeFlavor;

    public NodeFlavorTuning(Flavor nodeFlavor) {
        this.nodeFlavor = nodeFlavor;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        tuneDiskWriteSpeed(builder);
        tuneDocumentStoreMaxFileSize(builder.summary.log);
        tuneDocumentStoreNumThreads(builder.summary.log);
        tuneFlushStrategyMemoryLimits(builder.flush.memory);
        tuneFlushStrategyTlsSize(builder.flush.memory);
    }

    private void tuneDiskWriteSpeed(ProtonConfig.Builder builder) {
        if (!nodeFlavor.hasFastDisk()) {
            builder.hwinfo.disk.writespeed(40);
        }
    }

    private void tuneDocumentStoreMaxFileSize(ProtonConfig.Summary.Log.Builder builder) {
        double memoryGb = nodeFlavor.getMinMainMemoryAvailableGb();
        long fileSizeBytes = 4 * GB;
        if (memoryGb <= 12.0) {
            fileSizeBytes = 256 * MB;
        } else if (memoryGb < 24.0) {
            fileSizeBytes = 512 * MB;
        } else if (memoryGb <= 64.0) {
            fileSizeBytes = 1 * GB;
        }
        builder.maxfilesize(fileSizeBytes);
    }

    private void tuneDocumentStoreNumThreads(ProtonConfig.Summary.Log.Builder builder) {
        builder.numthreads(max(8, (int)nodeFlavor.getMinCpuCores()/2));
    }

    private void tuneFlushStrategyMemoryLimits(ProtonConfig.Flush.Memory.Builder builder) {
        long memoryLimitBytes = (long) ((nodeFlavor.getMinMainMemoryAvailableGb() / 8) * GB);
        builder.maxmemory(memoryLimitBytes);
        builder.each.maxmemory(memoryLimitBytes);
    }

    private void tuneFlushStrategyTlsSize(ProtonConfig.Flush.Memory.Builder builder) {
        long tlsSizeBytes = (long) ((nodeFlavor.getMinDiskAvailableGb() * 0.07) * GB);
        tlsSizeBytes = min(tlsSizeBytes, 100 * GB);
        builder.maxtlssize(tlsSizeBytes);
    }

}
