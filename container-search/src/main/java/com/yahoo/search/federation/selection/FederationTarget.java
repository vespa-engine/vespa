// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.selection;

import java.util.Optional;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a search chain that the federation searcher should send a query to,
 * along with a timeout and
 * custom data reserved for use by the TargetSelector.
 *
 * @author Tony Vaagenes
 */
public final class FederationTarget<T> {

    private final Chain<Searcher> chain;
    private final FederationOptions federationOptions;
    private final T customData;

    public FederationTarget(Chain<Searcher> chain, FederationOptions federationOptions, T customData) {
        checkNotNull(chain);
        checkNotNull(federationOptions);

        this.chain = chain;
        this.federationOptions = federationOptions;
        this.customData = customData;
    }

    public Chain<Searcher> getChain() {
        return chain;
    }

    public FederationOptions getFederationOptions() {
        return federationOptions;
    }

    /**
     * Any data that the TargetSelector wants to associate with this target.
     * Owned exclusively by the TargetSelector that created this instance.
     */
    public T getCustomData() {
        return customData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FederationTarget that = (FederationTarget) o;

        if (!chain.equals(that.chain)) return false;
        if (customData != null ? !customData.equals(that.customData) : that.customData != null) return false;
        if (!federationOptions.equals(that.federationOptions)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = chain.hashCode();
        result = 31 * result + federationOptions.hashCode();
        return result;
    }

}
