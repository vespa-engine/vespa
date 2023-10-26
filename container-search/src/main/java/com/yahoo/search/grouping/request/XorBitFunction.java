// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an xor-function in a {@link GroupingExpression}. It evaluates to a long that equals the xor of
 * 'width' bits over the binary representation of the result of the argument.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class XorBitFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param exp     The expression to evaluate.
     * @param numBits The number of bits of the expression value to xor.
     */
    public XorBitFunction(GroupingExpression exp, int numBits) {
        this(null, null, Arrays.asList(exp, new LongValue(numBits)));
    }

    private XorBitFunction(String label, Integer level, List<GroupingExpression> exp) {
        super("xorbit", label, level, exp);
    }

    @Override
    public XorBitFunction copy() {
        return new XorBitFunction(getLabel(),
                                  getLevelOrNull(),
                                  args().stream().map(arg -> arg.copy()).toList());
    }

    /**
     * Returns the number of bits of the expression value to xor.
     *
     * @return The bit count.
     */
    public int getNumBits() {
        return ((LongValue)getArg(1)).getValue().intValue();
    }
}

