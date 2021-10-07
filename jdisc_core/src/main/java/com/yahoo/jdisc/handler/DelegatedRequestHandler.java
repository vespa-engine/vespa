// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.jdisc.handler;

public interface DelegatedRequestHandler extends RequestHandler {
    RequestHandler getDelegate();

    default RequestHandler getDelegateRecursive() {
        RequestHandler delegate = getDelegate();
        while(delegate instanceof DelegatedRequestHandler) {
            delegate = ((DelegatedRequestHandler) delegate).getDelegate();
        }
        return delegate;
    }
}
