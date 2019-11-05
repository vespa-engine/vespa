// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.config.search.core.ProtonConfig;

import static java.lang.Long.min;

/**
 * Tuning of proton config for a search node based on the node flavor of that node.
 *
 * @author geirst
 */
public class NodeFlavorTuning implements ProtonConfig.Producer {

    static long MB = 1024 * 1024;
    static long GB = MB * 1024;
    private final Flavor nodeFlavor;
    private final int redundancy;
    private final int searchableCopies;


    public NodeFlavorTuning(Flavor nodeFlavor, int redundancy, int searchableCopies) {
        this.nodeFlavor = nodeFlavor;
        this.redundancy = redundancy;
        this.searchableCopies = searchableCopies;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        setHwInfo(builder);
        tuneDiskWriteSpeed(builder);
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
            long numDocs = (long)nodeFlavor.getMinMainMemoryAvailableGb()*GB/64L;
            builder.allocation.initialnumdocs(numDocs/Math.max(searchableCopies, redundancy));
        }
    }

    private void tuneSummaryCache(ProtonConfig.Summary.Cache.Builder builder) {
        long memoryLimitBytes = (long) ((nodeFlavor.getMinMainMemoryAvailableGb() * 0.05) * GB);
        builder.maxbytes(memoryLimitBytes);
    }

    private void setHwInfo(ProtonConfig.Builder builder) {
        builder.hwinfo.disk.shared(nodeFlavor.getType().equals(Flavor.Type.DOCKER_CONTAINER));
        builder.hwinfo.cpu.cores((int)nodeFlavor.resources().vcpu());
        builder.hwinfo.memory.size((long)nodeFlavor.resources().memoryGb() * GB);
        builder.hwinfo.disk.size((long)nodeFlavor.resources().diskGb() * GB);
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

    private void tuneSummaryReadIo(ProtonConfig.Summary.Read.Builder builder) {
        if (nodeFlavor.hasFastDisk()) {
            builder.io(ProtonConfig.Summary.Read.Io.DIRECTIO);
        }
    }

    private void tuneSearchReadIo(ProtonConfig.Search.Mmap.Builder builder) {
        if (nodeFlavor.hasFastDisk()) {
            builder.advise(ProtonConfig.Search.Mmap.Advise.RANDOM);
        }
    }

    private void tuneWriteFilter(ProtonConfig.Writefilter.Builder builder) {
        // "Reserve" 1GB of memory for other processes running on the content node (config-proxy, cluster-controller, metrics-proxy)
        double reservedMemoryGb = 1;
        double defaultMemoryLimit = new ProtonConfig.Writefilter(new ProtonConfig.Writefilter.Builder()).memorylimit();
        double scaledMemoryLimit = ((nodeFlavor.getMinMainMemoryAvailableGb() - reservedMemoryGb) * defaultMemoryLimit) / nodeFlavor.getMinMainMemoryAvailableGb();
        builder.memorylimit(scaledMemoryLimit);
    }

}
