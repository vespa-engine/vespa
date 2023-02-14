// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.processing;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chains;

/**
 * Root config producer for processing
 *
 * @author  bratseth
 */
public class ProcessingChains extends Chains<ProcessingChain> {

    public static final BindingPattern[] defaultBindings = new BindingPattern[]{SystemBindingPattern.fromHttpPath("/processing/*")};


    public ProcessingChains(TreeConfigProducer<? super Chains> parent, String subId) {
        super(parent, subId);
    }

}
