// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is in {@link Group} instances where the identifying expression evaluated to null. For example, hits that
 * fall outside the buckets of a {@link com.yahoo.search.grouping.request.PredefinedFunction} are added to an
 * auto-generated group with this id.
 *
 * @author Simon Thoresen Hult
 */
public class NullId extends GroupId {

    /**
     * Constructs a new instance of this class.
     */
    public NullId() {
        super("null");
    }
}
