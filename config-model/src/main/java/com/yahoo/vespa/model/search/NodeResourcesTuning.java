// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import static java.lang.Long.min;

/**
 * Tuning of proton config for a search node based on the resources on the node.
 *
 * @author geirst
 */
public class NodeResourcesTuning implements ProtonConfig.Producer {

    final static long MB = 1024 * 1024;
    public final static long GB = MB * 1024;
    // This is an approximate number base on observation of a node using 33G memory with 765M docs
    private final static long MEMORY_COST_PER_DOCUMENT_STORE_ONLY = 46L;
    private final NodeResources resources;
    private final int threadsPerSearch;
    private final boolean combined;

    // "Reserve" 0.5GB of memory for other processes running on the content node (config-proxy, metrics-proxy).
    public static final double reservedMemoryGb = 0.5;

    public NodeResourcesTuning(NodeResources resources,
                               int threadsPerSearch,
                               boolean combined) {
        this.resources = resources;
        this.threadsPerSearch = threadsPerSearch;
        this.combined = combined;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        setHwInfo(builder);
        tuneDiskWriteSpeed(builder);
        tuneRequestThreads(builder);
        tuneDocumentStoreMaxFileSize(builder.summary.log);
        tuneFlushStrategyMemoryLimits(builder.flush.memory);
        tuneFlushStrategyTlsSize(builder.flush.memory);
        tuneSummaryReadIo(builder.summary.read);
        tuneSummaryCache(builder.summary.cache);
        tuneSearchReadIo(builder.search.mmap);
        for (ProtonConfig.Documentdb.Builder dbb : builder.documentdb) {
            getConfig(dbb);
        }
    }

    private void getConfig(ProtonConfig.Documentdb.Builder builder) {
        ProtonConfig.Documentdb dbCfg = builder.build();
        if (dbCfg.mode() != ProtonConfig.Documentdb.Mode.Enum.INDEX) {
            long numDocs = (long)usableMemoryGb() * GB / MEMORY_COST_PER_DOCUMENT_STORE_ONLY;
            builder.allocation.initialnumdocs(numDocs);
        }
    }

    private void tuneSummaryCache(ProtonConfig.Summary.Cache.Builder builder) {
        long memoryLimitBytes = (long) ((usableMemoryGb() * 0.05) * GB);
        builder.maxbytes(memoryLimitBytes);
    }

    private void setHwInfo(ProtonConfig.Builder builder) {
        builder.hwinfo.disk.shared(true);
        builder.hwinfo.cpu.cores((int)resources.vcpu());
        builder.hwinfo.memory.size((long)(usableMemoryGb() * GB));
        builder.hwinfo.disk.size((long)(resources.diskGb() * GB));
    }

    private void tuneDiskWriteSpeed(ProtonConfig.Builder builder) {
        if (resources.diskSpeed() != NodeResources.DiskSpeed.fast) {
            builder.hwinfo.disk.writespeed(40);
        }
    }

    private void tuneDocumentStoreMaxFileSize(ProtonConfig.Summary.Log.Builder builder) {
        double memoryGb = usableMemoryGb();
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

    private void tuneFlushStrategyMemoryLimits(ProtonConfig.Flush.Memory.Builder builder) {
        long memoryLimitBytes = (long) ((usableMemoryGb() / 8) * GB);
        builder.maxmemory(memoryLimitBytes);
        builder.each.maxmemory(memoryLimitBytes);
    }

    private void tuneFlushStrategyTlsSize(ProtonConfig.Flush.Memory.Builder builder) {
        long tlsSizeBytes = (long) ((resources.diskGb() * 0.07) * GB);
        tlsSizeBytes = min(tlsSizeBytes, 100 * GB);
        builder.maxtlssize(tlsSizeBytes);
    }

    private void tuneSummaryReadIo(ProtonConfig.Summary.Read.Builder builder) {
        if (resources.diskSpeed() == NodeResources.DiskSpeed.fast) {
            builder.io(ProtonConfig.Summary.Read.Io.DIRECTIO);
        }
    }

    private void tuneSearchReadIo(ProtonConfig.Search.Mmap.Builder builder) {
        if (resources.diskSpeed() == NodeResources.DiskSpeed.fast) {
            builder.advise(ProtonConfig.Search.Mmap.Advise.RANDOM);
        }
    }

    private void tuneRequestThreads(ProtonConfig.Builder builder) {
        int numCores = (int)Math.ceil(resources.vcpu());
        builder.numsearcherthreads(numCores*threadsPerSearch);
        builder.numsummarythreads(numCores);
        builder.numthreadspersearch(threadsPerSearch);
    }

    /** Returns the memory we can expect will be available for the content node processes */
    private double usableMemoryGb() {
        double usableMemoryGb = resources.memoryGb() - reservedMemoryGb;
        if ( ! combined) {
            return usableMemoryGb;
        }

        double fractionTakenByContainer = ApplicationContainerCluster.heapSizePercentageOfTotalNodeMemoryWhenCombinedCluster * 1e-2;
        return usableMemoryGb * (1 - fractionTakenByContainer);
    }

}
