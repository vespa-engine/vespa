// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentId;
import com.yahoo.processing.request.Properties;

/**
 * TODO: What is this?
 *
* @author Tony Vaagenes
*/
public class SingleTarget extends Target {
    private final SearchChainInvocationSpec searchChainInvocationSpec;

    public SingleTarget(ComponentId id, SearchChainInvocationSpec searchChainInvocationSpec, boolean isDerived) {
        super(id, isDerived);
        this.searchChainInvocationSpec = searchChainInvocationSpec;
    }

    @Override
    public SearchChainInvocationSpec responsibleSearchChain(Properties queryProperties) {
        return searchChainInvocationSpec;
    }

    @Override
    public String searchRefDescription() {
        return localId.toString();
    }

    @Override
    void freeze() {}

    public final boolean useByDefault() {
        return searchChainInvocationSpec.federationOptions.getUseByDefault();
    }
}
