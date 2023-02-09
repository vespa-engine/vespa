// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.handler;

import java.util.Optional;

public interface DelegatedRequestHandler extends RequestHandler {
    RequestHandler getDelegate();

    default RequestHandler getDelegateRecursive() {
        RequestHandler delegate = getDelegate();
        while(delegate instanceof DelegatedRequestHandler) {
            delegate = ((DelegatedRequestHandler) delegate).getDelegate();
        }
        return delegate;
    }

    /** Find delegated request handler recursively */
    static RequestHandler resolve(RequestHandler h) {
        if (h instanceof DelegatedRequestHandler dh) return dh.getDelegateRecursive();
        return h;
    }

    /**
     * Find delegated request handler of specified type recursively
     * Note that the returned handler may not be the innermost handler.
     */
    static <T extends RequestHandler> Optional<T> resolve(Class<T> type, RequestHandler h) {
        T candidate = type.isInstance(h) ? type.cast(h) : null;
        while (h instanceof DelegatedRequestHandler) {
            h = ((DelegatedRequestHandler) h).getDelegate();
            if (type.isInstance(h)) candidate = type.cast(h);
        }
        return Optional.ofNullable(candidate);
    }

}
