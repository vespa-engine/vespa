// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts.bindings;

/**
 * @author freva
 */
public interface TemporaryCredentialsResponse<T> {
    T credentials();
}
