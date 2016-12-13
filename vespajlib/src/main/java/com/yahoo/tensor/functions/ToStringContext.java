package com.yahoo.tensor.functions;

import com.google.common.annotations.Beta;

/**
 * A context which is passed down to all nested functions when returning a string representation.
 * The default implementation is empty as this library does not in itself have any need for a
 * context.
 *
 * @author bratseth
 */
@Beta
public interface ToStringContext {

    static ToStringContext empty() { return new ToStringContext() {}; }

}
