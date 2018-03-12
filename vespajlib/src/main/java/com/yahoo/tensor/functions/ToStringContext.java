// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

/**
 * A context which is passed down to all nested functions when returning a string representation.
 * The default implementation is empty as this library does not in itself have any need for a
 * context.
 *
 * @author bratseth
 */
public interface ToStringContext {

    static ToStringContext empty() { return new ToStringContext() {}; }

}
