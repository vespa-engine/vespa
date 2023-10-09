// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a grouping operation that processes each element of the input list separately, as opposed to {@link
 * AllOperation} which processes that list as a whole.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class EachOperation extends GroupingOperation {

    /**
     * Constructs a new instance of this class.
     */
    public EachOperation() {
        super("each", null);
    }

    private EachOperation(GroupingOperation parentOfCopy,
                          String image,
                          String label,
                          List<GroupingExpression> orderBy,
                          List<GroupingExpression> outputs,
                          List<GroupingOperation> children,
                          Map<String, GroupingExpression> aliases,
                          Set<String> hints,
                          GroupingExpression groupBy,
                          String where,
                          boolean forceSinglePass,
                          double accuracy,
                          int precision,
                          int level,
                          int max) {
        super(parentOfCopy, image, label, orderBy, outputs, children, aliases, hints, groupBy, where, forceSinglePass, accuracy, precision, level, max);
    }

    @Override
    public EachOperation copy(GroupingOperation parentOfCopy) {
        return new EachOperation(parentOfCopy,
                                 getImage(),
                                 getLabel(),
                                 getOrderBy(),
                                 getOutputs(),
                                 getChildren(),
                                 getAliases(),
                                 getHints(),
                                 getGroupBy(),
                                 getWhere(),
                                 getForceSinglePass(),
                                 getAccuracy(),
                                 getPrecision(),
                                 getLevel(),
                                 getMax());
    }

    @Override
    public void resolveLevel(int level) {
        if (level == 0) {
            throw new IllegalArgumentException("Operation '" + this + "' can not operate on " + getLevelDesc(level) + ".");
        }
        super.resolveLevel(level - 1);
    }
}
