// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.chain.Chains;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Holder for components and options related to either processing/search/docproc
 * for a container cluster.
 *
 * @author gjoranv
 * @since 5.1.9
 */
public abstract class ContainerSubsystem<CHAINS extends Chains<?>> {

    private final CHAINS chains;

    public ContainerSubsystem(CHAINS chains) {
        this.chains = chains;
    }

    @NonNull
    public CHAINS getChains() {
        if (chains == null)
            throw new IllegalStateException("Null chains for " + this);
        return chains;
    }

}
