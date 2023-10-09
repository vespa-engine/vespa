// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents an md5-function in a {@link GroupingExpression}. It evaluates to a long that equals the md5 of
 * the result of the argument.
 *
 * @author Simon Thoresen Hult
 */
public class Md5Function extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp     The expression to evaluate.
     * @param numBits The number of bits of the md5 to include.
     */
    public Md5Function(GroupingExpression exp, int numBits) {
        this(null, null, exp, new LongValue(numBits));
    }

    private Md5Function(String label, Integer level, GroupingExpression exp, LongValue numBits) {
        super("md5", label, level, Arrays.asList(exp, numBits));
    }

    @Override
    public Md5Function copy() {
        return new Md5Function(getLabel(), getLevelOrNull(), getArg(0).copy(), (LongValue)getArg(1).copy());
    }

    /**
     * Returns the number of bits of the md5 to include in the evaluated result.
     *
     * @return The bit count.
     */
    public int getNumBits() {
        return ((LongValue)getArg(1)).getValue().intValue();
    }
}

