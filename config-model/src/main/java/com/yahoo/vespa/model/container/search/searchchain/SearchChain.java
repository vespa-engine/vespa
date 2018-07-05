// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.vespa.model.container.component.chain.Chain;

import java.util.Collections;
import java.util.List;

/**
 * Represents a search chain in the vespa model.
 *
 * @author Tony Vaagenes
 */
public class SearchChain extends Chain<Searcher<?>> {

    public SearchChain(ChainSpecification specWithoutInnerSearchers) {
        super(specWithoutInnerSearchers);
    }

    public FederationOptions federationOptions() {
        return new FederationOptions().setUseByDefault(true);
    }

    //A list of documents types that this search chain provides results for, empty if unknown
    public List<String> getDocumentTypes() {
        return Collections.emptyList();
    }

}
