// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

/**
 * @author Henning Baldersheim
 */
public record ResolveResult(SearchChainInvocationSpec invocationSpec, String errorMsg) {
    ResolveResult(SearchChainInvocationSpec invocationSpec) {
        this(invocationSpec, null);
    }
    ResolveResult(String errorMsg) {
        this(null, errorMsg);
    }
}
