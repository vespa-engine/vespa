// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 */
public class NormalizeSubjectFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp The expression to evaluate, must evaluate to a string.
     */
    public NormalizeSubjectFunction(GroupingExpression exp) {
        this(null, null, exp);
    }

    private NormalizeSubjectFunction(String label, Integer level, GroupingExpression exp) {
        super("normalizesubject", label, level, Arrays.asList(exp));
    }

    @Override
    public NormalizeSubjectFunction copy() {
        return new NormalizeSubjectFunction(getLabel(), getLevelOrNull(), getArg(0).copy());
    }

}

