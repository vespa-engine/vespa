// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class Searcher<T extends ChainedComponentModel> extends ChainedComponent<T> {

    public Searcher(T model) {
        super(model);
    }

    protected SearchChains getSearchChains() {
        var ancestor = getParent();
        while (!(ancestor instanceof SearchChains)) {
            ancestor = ancestor.getParent();
        }
        return (SearchChains)ancestor;
    }

}
