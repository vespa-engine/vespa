// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainedComponentModel;

/**
 * @author Tony Vaagenes
 * @deprecated
 */
// TODO: Remove on Vespa 7
@Deprecated
public class HttpProviderSearcher extends Searcher<ChainedComponentModel> {

    public HttpProviderSearcher(ChainedComponentModel model) {
        super(model);
    }


}
