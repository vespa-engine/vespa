// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.processing;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.Chains;

/**
 * Root config producer for processing
 *
 * @author  bratseth
 */
public class ProcessingChains extends Chains<ProcessingChain> {

    public static final String[] defaultBindings = new String[] {"http://*/processing/*"};


    public ProcessingChains(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
    }

}
