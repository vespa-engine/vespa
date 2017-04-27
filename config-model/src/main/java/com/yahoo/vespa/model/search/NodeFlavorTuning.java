package com.yahoo.vespa.model.search;

import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.config.search.core.ProtonConfig;

/**
 * Tuning of proton config for a search node based on the node flavor of that node.
 *
 * @author geirst
 */
public class NodeFlavorTuning implements ProtonConfig.Producer {

    private final Flavor nodeFlavor;

    public NodeFlavorTuning(Flavor nodeFlavor) {
        this.nodeFlavor = nodeFlavor;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        tuneDiskWriteSpeed(builder);
        tuneDocumentStoreMaxFileSize(builder);
    }

    private void tuneDiskWriteSpeed(ProtonConfig.Builder builder) {
        ProtonConfig.Hwinfo.Disk.Builder diskInfo = new ProtonConfig.Hwinfo.Disk.Builder();
        if (!nodeFlavor.hasFastDisk()) {
            diskInfo.writespeed(40);
        }
        builder.hwinfo(new ProtonConfig.Hwinfo.Builder().disk(diskInfo));
    }

    private void tuneDocumentStoreMaxFileSize(ProtonConfig.Builder builder) {
        ProtonConfig.Summary.Log.Builder logBuilder = new ProtonConfig.Summary.Log.Builder();
        double memoryGb = nodeFlavor.getMinMainMemoryAvailableGb();
        if (memoryGb <= 12.0) {
            logBuilder.maxfilesize(256 * MB);
        } else if (memoryGb < 24.0) {
            logBuilder.maxfilesize(512 * MB);
        } else if (memoryGb <= 64.0) {
            logBuilder.maxfilesize(1 * GB);
        } else {
            logBuilder.maxfilesize(4 * GB);
        }
        builder.summary(new ProtonConfig.Summary.Builder().log(logBuilder));
    }

    static long MB = 1024 * 1024;
    static long GB = MB * 1024;
}
