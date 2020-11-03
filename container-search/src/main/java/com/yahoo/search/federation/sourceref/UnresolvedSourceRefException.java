// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentSpecification;

/**
 * @author Tony Vaagenes
 */
@SuppressWarnings("serial")
class UnresolvedSourceRefException extends UnresolvedSearchChainException {
    UnresolvedSourceRefException(String msg) {
        super(msg);
    }


    static UnresolvedSearchChainException createForMissingSourceRef(ComponentSpecification source) {
        return new UnresolvedSourceRefException("Could not resolve source ref '" + source + "'.");
    }
}
