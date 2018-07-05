// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.List;
import java.util.Objects;

/**
 * Specifices which search chain should be run and how it should be run.
 * This is a value object.
 *
 * @author Tony Vaagenes
 */
public class SearchChainInvocationSpec implements Cloneable {

    public final ComponentId searchChainId;

    /** The source to invoke, or null if none */
    public final ComponentId source;
    /** The provider to invoke, or null if none */
    public final ComponentId provider;

    public final FederationOptions federationOptions;
    public final ImmutableList<String> documentTypes;

    SearchChainInvocationSpec(ComponentId searchChainId,
                              ComponentId source, ComponentId provider, FederationOptions federationOptions,
                              List<String> documentTypes) {
        this.searchChainId = searchChainId;
        this.source = source;
        this.provider = provider;
        this.federationOptions = federationOptions;
        this.documentTypes = ImmutableList.copyOf(documentTypes);
    }

    @Override
    public SearchChainInvocationSpec clone() throws CloneNotSupportedException {
        return (SearchChainInvocationSpec)super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! ( o instanceof SearchChainInvocationSpec)) return false;

        SearchChainInvocationSpec other = (SearchChainInvocationSpec)o;
        if ( ! Objects.equals(this.searchChainId, other.searchChainId)) return false;
        if ( ! Objects.equals(this.source, other.source)) return false;
        if ( ! Objects.equals(this.provider, other.provider)) return false;
        if ( ! Objects.equals(this.federationOptions, other.federationOptions)) return false;
        if ( ! Objects.equals(this.documentTypes, other.documentTypes)) return false;
        return true;
    }

    @Override
    public int hashCode() { 
        return Objects.hash(searchChainId, source, provider, federationOptions, documentTypes);
    }

}
