// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * Represents the filter expression for a {@link GroupingOperation}.
 *
 * @author bjorncs
 */
@Beta
public abstract class FilterExpression {
    // Force subclasses to override toString() as it's used for serializing a GroupingOperation instance to string :(
    public abstract String toString();

    /** Returns a deep copy of this */
    public abstract FilterExpression copy();
}
