// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class Filter extends ChainedComponent<ChainedComponentModel> {

    public Filter(ChainedComponentModel model) {
        super(model);
    }

    public FilterConfigProvider addAndInjectConfigProvider() {
        FilterConfigProvider filterConfigProvider = new FilterConfigProvider(model);
        addComponent(filterConfigProvider);
        inject(filterConfigProvider);
        return filterConfigProvider;
    }

}
