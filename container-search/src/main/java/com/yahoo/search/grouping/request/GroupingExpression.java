// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.javacc.UnicodeUtilities;

import java.util.List;

/**
 * This class represents an expression in a {@link GroupingOperation}. You may manually construct this expression, or
 * you may use the {@link com.yahoo.search.grouping.request.parser.GroupingParser} to generate one from a query-string.
 *
 * @author Simon Thoresen Hult
 */
public abstract class GroupingExpression extends GroupingNode {

    private Integer level = null;

    protected GroupingExpression(String image) {
        super(image);
    }

    /**
     * Resolves the conceptual level of this expression. This level represents the type of data that is consumed by this
     * expression, where level 0 is a single hit, level 1 is a group, level 2 is a list of groups, and so forth. This
     * method verifies the input level against the expression type, and recursively resolves the level of all argument
     * expressions.
     *
     * @param level The level of the input data.
     * @throws IllegalArgumentException Thrown if the level of this expression could not be resolved.
     * @throws IllegalStateException    Thrown if type failed to accept the number of arguments provided.
     */
    public void resolveLevel(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Expression '" + this + "' recurses through a single hit.");
        }
        this.level = level;
    }

    /**
     * Returns the conceptual level of this expression.
     *
     * @return The level.
     * @throws IllegalArgumentException Thrown if the level of this expression has not been resolved.
     * @see #resolveLevel(int)
     */
    public int getLevel() {
        if (level == null) {
            throw new IllegalStateException("Level for expression '" + this + "' has not been resolved.");
        }
        return level;
    }

    /**
     * Recursively calls {@link ExpressionVisitor#visitExpression(GroupingExpression)} for this expression and all of
     * its argument expressions.
     *
     * @param visitor The visitor to call.
     */
    public void visit(ExpressionVisitor visitor) {
        visitor.visitExpression(this);
    }

    /**
     * Returns a string description of the given list of expressions. This is a comma-separated list of the expressions
     * own {@link GroupingExpression#toString()} output.
     *
     * @param lst The list of expressions to output.
     * @return The string description.
     */
    public static String asString(List<GroupingExpression> lst) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0, len = lst.size(); i < len; ++i) {
            ret.append(lst.get(i));
            if (i < len - 1) {
                ret.append(", ");
            }
        }
        return ret.toString();
    }

    /**
     * Returns a string representation of an object that can be used in the 'image' constructor argument of {@link
     * GroupingNode}. This method ensures that strings are quoted, and that all complex characters are escaped.
     *
     * @param obj The object to output.
     * @return The string representation.
     */
    public static String asImage(Object obj) {
        if (!(obj instanceof String)) {
            return obj.toString();
        }
        return UnicodeUtilities.quote((String)obj, '"');
    }

    @Override
    public GroupingExpression setLabel(String label) {
        super.setLabel(label);
        return this;
    }
}
