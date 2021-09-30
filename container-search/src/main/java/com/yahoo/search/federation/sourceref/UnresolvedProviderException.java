// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;

/**
 * @author Tony Vaagenes
 */
@SuppressWarnings("serial")
class UnresolvedProviderException extends UnresolvedSearchChainException {
    UnresolvedProviderException(String msg) {
        super(msg);
    }

    static UnresolvedSearchChainException createForMissingProvider(ComponentId source,
                                                                   ComponentSpecification provider) {
        return new UnresolvedProviderException("No provider '" + provider + "' for source '" + source + "'.");
    }
}
