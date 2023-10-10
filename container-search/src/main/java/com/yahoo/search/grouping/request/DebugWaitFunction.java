// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class represents debug_wait function in a {@link GroupingExpression}. For each hit evaluated,
 * it waits for the time specified as the second argument. The third argument specifies if the wait
 * should be a busy-wait or not. The first argument is then evaluated.
 *
 * @author Ulf Lilleengen
 */
public class DebugWaitFunction extends FunctionNode {

    /**
     * Constructs a new instance of this class.
     *
     * @param arg1 The first compulsory argument, the expression to proxy.
     * @param arg2 The second compulsory argument, must evaluate to a positive number.
     * @param arg3 The third compulsory argument, specifying busy wait or not.
     */
    public DebugWaitFunction(GroupingExpression arg1, DoubleValue arg2, BooleanValue arg3) {
        this(null, null, arg1, arg2, arg3);
    }

    private DebugWaitFunction(String label, Integer level, GroupingExpression arg1, DoubleValue arg2, BooleanValue arg3) {
        super("debugwait", label, level, Arrays.asList(arg1, arg2, arg3));
    }

    @Override
    public DebugWaitFunction copy() {
        return new DebugWaitFunction(getLabel(),
                                     getLevelOrNull(),
                                     getArg(0).copy(),
                                     (DoubleValue)getArg(1).copy(),
                                     (BooleanValue)getArg(2).copy());
    }

    /**
     * Returns the time to wait when evaluating this function.
     *
     * @return the number of seconds to wait.
     */
    public double getWaitTime() {
        return ((DoubleValue)getArg(1)).getValue();
    }

    /**
     * Returns whether or not the debug node should busy-wait.
     *
     * @return true if busy-wait, false if not.
     */
    public boolean getBusyWait() {
        return ((BooleanValue)getArg(2)).getValue();
    }
}
