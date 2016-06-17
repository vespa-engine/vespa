// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.processing;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.Chains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Root config producer for processing
 *
 * @author  bratseth
 * @since   5.1.6
 */
public class ProcessingChains extends Chains<ProcessingChain> {
    public static final String[] defaultBindings = new String[]
            {"http://*/processing/*", "https://*/processing/*"};


    public ProcessingChains(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
    }
}
