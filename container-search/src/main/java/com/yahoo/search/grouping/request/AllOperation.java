// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a grouping operation that processes the input list as a whole, as opposed to {@link EachOperation} which
 * processes each element of that list separately.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class AllOperation extends GroupingOperation {

    /**
     * Constructs a new instance of this class.
     */
    public AllOperation() {
        super("all", null);
    }

    private AllOperation(GroupingOperation parentOfCopy,
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
    public AllOperation copy(GroupingOperation parentOfCopy) {
        return new AllOperation(parentOfCopy,
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

}
