// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class represents a timestamp-formatter function in a {@link GroupingExpression}. It evaluates to a string on the
 * form "YYYY-MM-DD" of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DateFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a long.
     */
    public DateFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private DateFunction(String label, Integer level, GroupingExpression exp) {
        super("time.date", label, level, Arrays.asList(exp));
    }

    @Override
    public DateFunction copy() {
        return new DateFunction(getLabel(),
                                getLevelOrNull(),
                                getArg(0).copy());
    }

}
