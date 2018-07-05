// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.request.GroupingOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class VespaGroupingStep {

    private final GroupingOperation operation;
    private final List<Continuation> continuations = new ArrayList<>();

    public VespaGroupingStep(GroupingOperation operation) {
        this.operation = operation;
    }

    public GroupingOperation getOperation() {
        return operation;
    }

    public List<Continuation> continuations() {
        return continuations;
    }
}
