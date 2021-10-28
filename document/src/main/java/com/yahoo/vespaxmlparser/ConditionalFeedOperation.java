// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.TestAndSetCondition;

public class ConditionalFeedOperation extends FeedOperation {

    private final TestAndSetCondition condition;

    protected ConditionalFeedOperation(Type type) {
        super(type);
        this.condition = TestAndSetCondition.NOT_PRESENT_CONDITION;
    }
    protected ConditionalFeedOperation(Type type, TestAndSetCondition condition) {
        super(type);
        this.condition = condition;
    }

    @Override
    public TestAndSetCondition getCondition() {
        return condition;
    }

}
