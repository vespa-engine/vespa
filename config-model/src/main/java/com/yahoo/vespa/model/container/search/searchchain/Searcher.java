// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;

/**
 * @author gjoranv
 * @author tonytv
 */
public class Searcher<T extends ChainedComponentModel> extends ChainedComponent<T> {

    public Searcher(T model) {
        super(model);
    }

    protected SearchChains getSearchChains() {
        AbstractConfigProducer ancestor = getParent();
        while (!(ancestor instanceof SearchChains)) {
            ancestor = ancestor.getParent();
        }
        return (SearchChains)ancestor;
    }

}
