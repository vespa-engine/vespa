// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.processing.request.Properties;

/**
 * TODO: What's this?
 *
 * @author tonytv
 */
public abstract class Target extends AbstractComponent {

    final ComponentId localId;
    final boolean isDerived;

    Target(ComponentId localId, boolean derived) {
        super(localId);
        this.localId = localId;
        isDerived = derived;
    }

    Target(ComponentId localId) {
        this(localId, false);
    }

    public abstract SearchChainInvocationSpec responsibleSearchChain(Properties queryProperties) throws UnresolvedSearchChainException;
    public abstract String searchRefDescription();

    abstract void freeze();

}
