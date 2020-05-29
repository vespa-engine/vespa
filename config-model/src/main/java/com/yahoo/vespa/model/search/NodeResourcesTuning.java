// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.config.search.core.ProtonConfig;

import static java.lang.Long.min;

/**
 * Tuning of proton config for a search node based on the resources on the node.
 *
 * @author geirst
 */
public class NodeResourcesTuning implements ProtonConfig.Producer {

    final static long MB = 1024 * 1024;
    final static long GB = MB * 1024;
    private final NodeResources resources;
    private final int redundancy;
    private final int searchableCopies;
    private final int threadsPerSearch;


    public NodeResourcesTuning(NodeResources resources, int redundancy, int searchableCopies) {
        this(resources, redundancy, searchableCopies, 1);
    }

    public NodeResourcesTuning(NodeResources resources, int redundancy, int searchableCopies, int threadsPerSearch) {
        this.resources = resources;
        this.redundancy = redundancy;
        this.searchableCopies = searchableCopies;
        this.threadsPerSearch = threadsPerSearch;
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
        tuneWriteFilter(builder.writefilter);
        for (ProtonConfig.Documentdb.Builder dbb : builder.documentdb) {
            getConfig(dbb);
        }
    }

    private void getConfig(ProtonConfig.Documentdb.Builder builder) {
        ProtonConfig.Documentdb dbCfg = builder.build();
        if (dbCfg.mode() != ProtonConfig.Documentdb.Mode.Enum.INDEX) {
            long numDocs = (long)resources.memoryGb() * GB / 64L;
            builder.allocation.initialnumdocs(numDocs/Math.max(searchableCopies, redundancy));
        }
    }

    private void tuneSummaryCache(ProtonConfig.Summary.Cache.Builder builder) {
        long memoryLimitBytes = (long) ((resources.memoryGb() * 0.05) * GB);
        builder.maxbytes(memoryLimitBytes);
    }

    private void setHwInfo(ProtonConfig.Builder builder) {
        builder.hwinfo.disk.shared(true);
        builder.hwinfo.cpu.cores((int)resources.vcpu());
        builder.hwinfo.memory.size((long)resources.memoryGb() * GB);
        builder.hwinfo.disk.size((long)resources.diskGb() * GB);
    }

    private void tuneDiskWriteSpeed(ProtonConfig.Builder builder) {
        if (resources.diskSpeed() != NodeResources.DiskSpeed.fast) {
            builder.hwinfo.disk.writespeed(40);
        }
    }

    private void tuneDocumentStoreMaxFileSize(ProtonConfig.Summary.Log.Builder builder) {
        double memoryGb = resources.memoryGb();
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
        long memoryLimitBytes = (long) ((resources.memoryGb() / 8) * GB);
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

    private void tuneWriteFilter(ProtonConfig.Writefilter.Builder builder) {
        // "Reserve" 1GB of memory for other processes running on the content node (config-proxy, cluster-controller, metrics-proxy)
        double reservedMemoryGb = 1;
        double defaultMemoryLimit = new ProtonConfig.Writefilter(new ProtonConfig.Writefilter.Builder()).memorylimit();
        double scaledMemoryLimit = ((resources.memoryGb() - reservedMemoryGb) * defaultMemoryLimit) / resources.memoryGb();
        builder.memorylimit(scaledMemoryLimit);
    }

}
