// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.vespa.model.container.component.chain.Chains;

/**
 * Holder for components and options related to either processing/search/docproc
 * for a container cluster.
 *
 * @author gjoranv
 */
public abstract class ContainerSubsystem<CHAINS extends Chains<?>> {

    private final CHAINS chains;

    public ContainerSubsystem(CHAINS chains) {
        this.chains = chains;
    }

    public CHAINS getChains() {
        if (chains == null)
            throw new IllegalStateException("Null chains for " + this);
        return chains;
    }

}
