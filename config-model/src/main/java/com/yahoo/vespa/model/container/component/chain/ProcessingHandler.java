// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.component.Handler;


/**
 * Represents a handler for processing chains.
 *
 * @author gjoranv
 */
public class ProcessingHandler<CHAINS extends Chains<?>>
        extends Handler
        implements ChainsConfig.Producer {

    // Cannot use the class object for ProcessingHandler, because its superclass is not accessible
    public static final String PROCESSING_HANDLER_CLASS = "com.yahoo.processing.handler.ProcessingHandler";

    protected final CHAINS chains;

    // Create a handler that uses the default threadpool for handlers
    public ProcessingHandler(CHAINS chains, BundleInstantiationSpecification spec) {
        this(chains, spec, null);
    }

    public ProcessingHandler(CHAINS chains, BundleInstantiationSpecification spec, ContainerThreadpool threadpool) {
        super(new ComponentModel(spec), threadpool);
        this.chains = chains;
    }

    @Override
    public void getConfig(ChainsConfig.Builder builder) {
        chains.getConfig(builder);
    }

}
