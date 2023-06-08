// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.Host;

import static java.lang.Long.min;
import static java.lang.Long.max;

/**
 * Tuning of proton config for a search node based on the resources on the node.
 *
 * @author geirst
 */
public class NodeResourcesTuning implements ProtonConfig.Producer {

    private final static double SUMMARY_FILE_SIZE_AS_FRACTION_OF_MEMORY = 0.02;
    private final static double SUMMARY_CACHE_SIZE_AS_FRACTION_OF_MEMORY = 0.04;
    private final static double MEMORY_GAIN_AS_FRACTION_OF_MEMORY = 0.08;
    private final static double MIN_MEMORY_PER_FLUSH_THREAD_GB = 11.0;
    private final static double TLS_SIZE_FRACTION = 0.02;
    final static long MB = 1024 * 1024;
    public final static long GB = MB * 1024;
    // This is an approximate number based on observation of a node using 33G memory with 765M docs
    private final static long MEMORY_COST_PER_DOCUMENT_STORE_ONLY = 46L;
    private final NodeResources resources;
    private final int threadsPerSearch;
    private final double fractionOfMemoryReserved;

    public NodeResourcesTuning(NodeResources resources,
                               int threadsPerSearch,
                               double fractionOfMemoryReserved) {
        this.resources = resources;
        this.threadsPerSearch = threadsPerSearch;
        this.fractionOfMemoryReserved = fractionOfMemoryReserved;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        setHwInfo(builder);
        tuneDiskWriteSpeed(builder);
        tuneRequestThreads(builder);
        tuneDocumentStoreMaxFileSize(builder.summary.log);
        tuneFlushStrategyMemoryLimits(builder.flush.memory);
        tuneFlushStrategyTlsSize(builder.flush.memory);
        tuneFlushConcurrentThreads(builder.flush);
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
        long memoryLimitBytes = (long) ((usableMemoryGb() * SUMMARY_CACHE_SIZE_AS_FRACTION_OF_MEMORY) * GB);
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
        long fileSizeBytes = (long) Math.max(256*MB, usableMemoryGb()*GB*SUMMARY_FILE_SIZE_AS_FRACTION_OF_MEMORY);
        builder.maxfilesize(fileSizeBytes);
    }

    private void tuneFlushStrategyMemoryLimits(ProtonConfig.Flush.Memory.Builder builder) {
        long memoryLimitBytes = (long) ((usableMemoryGb() * MEMORY_GAIN_AS_FRACTION_OF_MEMORY) * GB);
        builder.maxmemory(memoryLimitBytes);
        builder.each.maxmemory(memoryLimitBytes);
    }

    private void tuneFlushConcurrentThreads(ProtonConfig.Flush.Builder builder) {
        int max_concurrent = 2; // TODO bring slowly up towards 4
        if (usableMemoryGb() < MIN_MEMORY_PER_FLUSH_THREAD_GB) {
            max_concurrent = 1;
        }
        double min_concurrent_mem = usableMemoryGb() / MIN_MEMORY_PER_FLUSH_THREAD_GB;
        builder.maxconcurrent(Math.min(max_concurrent, (int)Math.ceil(min_concurrent_mem)));
    }

    private void tuneFlushStrategyTlsSize(ProtonConfig.Flush.Memory.Builder builder) {
        long tlsSizeBytes = (long) ((resources.diskGb() * TLS_SIZE_FRACTION) * GB);
        tlsSizeBytes = max(2*GB, min(tlsSizeBytes, 100 * GB));
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
        builder.numsearcherthreads(Math.min(((numCores*4 + threadsPerSearch - 1)/threadsPerSearch)*threadsPerSearch, numCores*threadsPerSearch));
        builder.numsummarythreads(numCores);
        builder.numthreadspersearch(threadsPerSearch);
    }

    /** Returns the memory we can expect will be available for the content node processes */
    private double usableMemoryGb() {
        double usableMemoryGb = resources.memoryGb() - Host.memoryOverheadGb;
        return usableMemoryGb * (1 - fractionOfMemoryReserved);
    }

}
