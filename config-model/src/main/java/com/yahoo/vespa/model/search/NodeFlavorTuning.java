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

    public NodeFlavorTuning(Flavor nodeFlavor) {
        this.nodeFlavor = nodeFlavor;
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
        for (ProtonConfig.Documentdb.Builder dbb : builder.documentdb) {
            getConfig(dbb);
        }
    }

    private void getConfig(ProtonConfig.Documentdb.Builder builder) {
        ProtonConfig.Documentdb dbCfg = builder.build();
        if (dbCfg.mode() != ProtonConfig.Documentdb.Mode.Enum.INDEX) {
            long numDocs = (long)nodeFlavor.memory().sizeInGb()*GB/64L;
            builder.allocation.initialnumdocs(numDocs);
        }
    }

    private void tuneSummaryCache(ProtonConfig.Summary.Cache.Builder builder) {
        long memoryLimitBytes = (long) ((nodeFlavor.memory().sizeInGb() * 0.05) * GB);
        builder.maxbytes(memoryLimitBytes);
    }

    private void setHwInfo(ProtonConfig.Builder builder) {
        builder.hwinfo.disk.size((long)nodeFlavor.disk().sizeInBase10Gb() * GB);
        builder.hwinfo.disk.shared(nodeFlavor.environment().equals(Flavor.Environment.DOCKER_CONTAINER));
        builder.hwinfo.memory.size((long)nodeFlavor.memory().sizeInGb() * GB);
        builder.hwinfo.cpu.cores((int)nodeFlavor.cpu().cores());
    }

    private void tuneDiskWriteSpeed(ProtonConfig.Builder builder) {
        if (!nodeFlavor.disk().isFast()) {
            builder.hwinfo.disk.writespeed(40);
        }
    }

    private void tuneDocumentStoreMaxFileSize(ProtonConfig.Summary.Log.Builder builder) {
        double memoryGb = nodeFlavor.memory().sizeInGb();
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
        long memoryLimitBytes = (long) ((nodeFlavor.memory().sizeInGb() / 8) * GB);
        builder.maxmemory(memoryLimitBytes);
        builder.each.maxmemory(memoryLimitBytes);
    }

    private void tuneFlushStrategyTlsSize(ProtonConfig.Flush.Memory.Builder builder) {
        long tlsSizeBytes = (long) ((nodeFlavor.disk().sizeInBase10Gb() * 0.07) * GB);
        tlsSizeBytes = min(tlsSizeBytes, 100 * GB);
        builder.maxtlssize(tlsSizeBytes);
    }

    private void tuneSummaryReadIo(ProtonConfig.Summary.Read.Builder builder) {
        if (nodeFlavor.disk().isFast()) {
            builder.io(ProtonConfig.Summary.Read.Io.DIRECTIO);
        }
    }

    private void tuneSearchReadIo(ProtonConfig.Search.Mmap.Builder builder) {
        if (nodeFlavor.disk().isFast()) {
            builder.advise(ProtonConfig.Search.Mmap.Advise.RANDOM);
        }
    }

}
