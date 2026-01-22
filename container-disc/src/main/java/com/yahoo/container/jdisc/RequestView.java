// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

import com.yahoo.jdisc.http.HttpRequest;

import java.net.URI;

/**
 * Read-only view of the request
 *
 * @author mortent
 */
public interface RequestView {
    HttpRequest.Method method();

    URI uri();
}
