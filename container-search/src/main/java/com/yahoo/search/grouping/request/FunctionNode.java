// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.*;

/**
 * This class represents a function in a {@link GroupingExpression}. Because it operate on other expressions (as opposed
 * to {@link AggregatorNode} and {@link DocumentValue} that operate on inputs), this expression type can be used at any
 * input level (see {@link GroupingExpression#resolveLevel(int)}).
 *
 * @author Simon Thoresen Hult
 */
public abstract class FunctionNode extends GroupingExpression implements Iterable<GroupingExpression> {

    private final List<GroupingExpression> args = new ArrayList<>();

    protected FunctionNode(String image, String label, Integer level, List<GroupingExpression> args) {
        super(image + "(" + asString(args) + ")", label, level);
        this.args.addAll(args);
    }

    /**
     * Returns the number of arguments that were given to this function at construction.
     *
     * @return The argument count.
     */
    public int getNumArgs() {
        return args.size();
    }

    /**
     * Returns the argument at the given index.
     *
     * @param i The index of the argument to return.
     * @return The argument at the given index.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public GroupingExpression getArg(int i) {
        return args.get(i);
    }

    /** Returns the arguments of this as a list which cannot be modified */
    // Note: If this is made public the returned list must be immutable
    protected List<GroupingExpression> args() { return args; }

    @Override
    public Iterator<GroupingExpression> iterator() {
        return Collections.unmodifiableList(args).iterator();
    }

    @Override
    public void resolveLevel(int level) {
        super.resolveLevel(level);
        for (GroupingExpression arg : args) {
            arg.resolveLevel(level);
        }
    }

    @Override
    public void visit(ExpressionVisitor visitor) {
        super.visit(visitor);
        for (GroupingExpression arg : args) {
            arg.visit(visitor);
        }
    }

    @SuppressWarnings("unchecked")
    protected static <T> List<T> asList(T arg1, T... argN) {
        return asList(Arrays.asList(arg1), Arrays.asList(argN));
    }

    @SuppressWarnings("unchecked")
    protected static <T> List<T> asList(T arg1, T arg2, T... argN) {
        return asList(Arrays.asList(arg1, arg2), Arrays.asList(argN));
    }

    protected static <T> List<T> asList(List<T> foo, List<T> bar) {
        List<T> ret = new LinkedList<>(foo);
        ret.addAll(bar);
        return ret;
    }

}
