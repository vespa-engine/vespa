// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component.chain;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.Handler;


/**
 * Represents a handler for processing chains.
 *
 * @author gjoranv
 */
public class ProcessingHandler<CHAINS extends Chains<?>>
        extends Handler<AbstractConfigProducer<?>>
        implements ChainsConfig.Producer {

    public static final String PROCESSING_HANDLER_CLASS = "com.yahoo.processing.handler.ProcessingHandler";

    protected final CHAINS chains;

    public ProcessingHandler(CHAINS chains, String handlerClass) {
        this(chains, BundleInstantiationSpecification.getInternalProcessingSpecificationFromStrings(handlerClass, null));
    }

    public ProcessingHandler(CHAINS chains, String handlerClass, String bundle) {
        this(chains, BundleInstantiationSpecification.getFromStrings(handlerClass, null, bundle));
    }

    private ProcessingHandler(CHAINS chains, BundleInstantiationSpecification spec) {
        super(new ComponentModel(spec, null));
        this.chains = chains;
    }

    @Override
    public void getConfig(ChainsConfig.Builder builder) {
        chains.getConfig(builder);
    }

}
