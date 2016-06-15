// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentId;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.List;

/**
 * Specifices which search chain should be run and how it should be run.
 *
 * @author tonytv
 */
public class SearchChainInvocationSpec implements Cloneable {
    public final ComponentId searchChainId;

    public final ComponentId source;
    public final ComponentId provider;

    public final FederationOptions federationOptions;
    public final List<String> documentTypes;

    SearchChainInvocationSpec(ComponentId searchChainId,
                              ComponentId source, ComponentId provider, FederationOptions federationOptions,
                              List<String> documentTypes) {
        this.searchChainId = searchChainId;
        this.source = source;
        this.provider = provider;
        this.federationOptions = federationOptions;
        this.documentTypes = documentTypes;
    }

    @Override
    public SearchChainInvocationSpec clone() throws CloneNotSupportedException {
        return (SearchChainInvocationSpec)super.clone();
    }
}
