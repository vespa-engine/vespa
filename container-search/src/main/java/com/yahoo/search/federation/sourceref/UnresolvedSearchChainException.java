// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

/**
 * Thrown if a search chain can not be resolved from one or more ids.
 * @author Tony Vaagenes
 */
@SuppressWarnings("serial")
public class UnresolvedSearchChainException extends Exception {
    public UnresolvedSearchChainException(String msg) {
        super(msg);
    }
}
