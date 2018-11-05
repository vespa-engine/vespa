// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.cache.QrBinaryCacheConfig;
import com.yahoo.search.cache.QrBinaryCacheRegionConfig;
import com.yahoo.search.federation.ProviderConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.search.federation.ProviderConfig.Node;
import static com.yahoo.search.federation.ProviderConfig.Yca;


/**
 * A generic provider, used when no type is given.
 *
 * @author Tony Vaagenes
 */
public class GenericProvider extends Provider implements ProviderConfig.Producer {

    /*
     * Config producer for the contained http searcher..
     */
    @SuppressWarnings("deprecation")
    public GenericProvider(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions) {
        super(specWithoutInnerSearchers, federationOptions);
    }

    @Override
    public void getConfig(ProviderConfig.Builder builder) {
    }

}
