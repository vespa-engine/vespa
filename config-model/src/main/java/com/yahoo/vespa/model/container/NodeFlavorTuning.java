// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.provision.Flavor;
import com.yahoo.search.config.QrStartConfig;

/**
 * Tuning of qr-start config for a container service based on the node flavor of that node.
 *
 * @author balder
 */
public class NodeFlavorTuning implements QrStartConfig.Producer {

    private final Flavor flavor;

    NodeFlavorTuning(Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm.availableProcessors(Math.max(2, (int)Math.ceil(flavor.getMinCpuCores())));
    }

}
