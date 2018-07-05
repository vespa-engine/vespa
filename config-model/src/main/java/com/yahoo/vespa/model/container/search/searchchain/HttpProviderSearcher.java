// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.binaryprefix.BinaryPrefix;
import com.yahoo.binaryprefix.BinaryScaledAmount;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.search.searchchain.model.federation.HttpProviderSpec;
import java.util.List;

/**

* @author Tony Vaagenes
*/
public class HttpProviderSearcher extends Searcher<ChainedComponentModel> {


    public HttpProviderSearcher(ChainedComponentModel model) {
        super(model);
    }


}
