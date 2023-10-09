// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.result.Relevance;

/**
 * This class represents the root {@link Group} in the grouping result model. This class adds a {@link Continuation}
 * object that can be used to paginate the result.
 *
 * @author Simon Thoresen Hult
 */
public class RootGroup extends Group {

    private final Continuation continuation;

    public RootGroup(int id, Continuation continuation) {
        super(new RootId(id), new Relevance(1.0));
        this.continuation = continuation;
    }

    public Continuation continuation() {
        return continuation;
    }
}
