// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

/**
 * A search chain that is intended to be used for federation (i.e. providers, sources)
 *
 * @author Tony Vaagenes
 */
abstract public class GenericTarget extends SearchChain {

    private final FederationOptions federationOptions;

    public GenericTarget(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions) {
        super(specWithoutInnerSearchers);
        this.federationOptions = federationOptions;
    }

    @Override
    public FederationOptions federationOptions() {
        FederationOptions defaultOptions = new FederationOptions().setUseByDefault(useByDefault());
        return federationOptions.inherit(defaultOptions);
    }

    /** The value for useByDefault in case the user have not specified any **/
    abstract protected boolean useByDefault();

}
