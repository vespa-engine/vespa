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
        ProtonConfig.Hwinfo.Disk.Builder diskInfo = new ProtonConfig.Hwinfo.Disk.Builder();
        if (!nodeFlavor.hasFastDisk()) {
            diskInfo.writespeed(40);
        }
        builder.hwinfo(new ProtonConfig.Hwinfo.Builder().disk(diskInfo));
    }
}
