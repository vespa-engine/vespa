// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This interface defines the necessary callback to recursively visit all {@link GroupingExpression} objects in a {@link
 * GroupingOperation}. It is used by the {@link com.yahoo.search.grouping.GroupingValidator} to ensure that all
 * referenced attributes are valid for the cluster being queried.
 *
 * @author Simon Thoresen Hult
 */
public interface ExpressionVisitor {

    /**
     * This method is called for every {@link GroupingExpression} object in the targeted {@link GroupingOperation}.
     *
     * @param exp The expression being visited.
     */
    public void visitExpression(GroupingExpression exp);
}
