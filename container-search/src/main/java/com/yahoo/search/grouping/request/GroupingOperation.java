// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;
import com.yahoo.collections.LazyMap;
import com.yahoo.collections.LazySet;
import com.yahoo.search.grouping.request.parser.GroupingParser;
import com.yahoo.search.grouping.request.parser.GroupingParserInput;
import com.yahoo.search.grouping.request.parser.ParseException;
import com.yahoo.search.grouping.request.parser.TokenMgrException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single node in a grouping operation tree. You may manually construct this tree, or you may
 * use the {@link #fromString(String)} method to generate one from a query-string. To execute, assign it to a {@link
 * com.yahoo.search.grouping.GroupingRequest} using the
 * {@link com.yahoo.search.grouping.GroupingRequest#setRootOperation(GroupingOperation)}
 * method.
 *
 * @author Simon Thoresen Hult
 */
public abstract class GroupingOperation extends GroupingNode {

    @Beta public static final int UNLIMITED_MAX = Integer.MAX_VALUE;

    private final List<GroupingExpression> orderBy = new ArrayList<>();
    private final List<GroupingExpression> outputs = new ArrayList<>();
    private final List<GroupingOperation> children = new ArrayList<>();
    private final Map<String, GroupingExpression> aliases = LazyMap.newHashMap();
    private final Set<String> hints = LazySet.newHashSet();

    private GroupingExpression groupBy = null;
    private GroupingOperation parent = null;
    private String where = null;
    private boolean forceSinglePass = false;
    private double accuracy = 0.95;
    private int precision = 0;
    private int level = -1;
    private int max = -1;

    protected GroupingOperation(String image, String label) {
        super(image, label);
    }

    protected GroupingOperation(GroupingOperation parentOfCopy,
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
        super(image, label);
        this.parent = parentOfCopy;
        orderBy.forEach(item -> this.orderBy.add(item.copy()));
        outputs.forEach(item -> this.outputs.add(item.copy()));
        children.forEach(item -> this.children.add(item.copy(this)));
        aliases.forEach((key, value) -> this.aliases.put(key, value.copy()));
        this.hints.addAll(hints);
        if (groupBy != null) this.groupBy = groupBy.copy();
        this.where = where;
        this.forceSinglePass = forceSinglePass;
        this.accuracy = accuracy;
        this.precision = precision;
        this.level = level;
        this.max = max;
    }

    /** Returns a deep copy of this */
    public abstract GroupingOperation copy(GroupingOperation parentOfCopy);

    /**
     * Registers an alias with this operation. An alias is made available to expressions in both this node and all child
     * nodes.
     *
     * @param id the id of the alias to put
     * @param exp the expression to associate with the id
     * @return this, to allow chaining
     */
    public GroupingOperation putAlias(String id, GroupingExpression exp) {
        aliases.put(id, exp);
        return this;
    }

    /**
     * Returns the alias associated with the given name. If no alias can be found in this node, this method queries its
     * parent grouping node. If the alias still can not be found, this method returns null.
     *
     * @param id the id of the alias to return
     * @return the expression associated with the id
     */
    public GroupingExpression getAlias(String id) {
        if (aliases.containsKey(id)) {
            return aliases.get(id);
        } else if (parent != null) {
            return parent.getAlias(id);
        } else {
            return null;
        }
    }

    /** Returns a direct, mutable copy of the aliases of this, never null */
    protected Map<String, GroupingExpression> getAliases() {
        return aliases;
    }

    /** Adds a hint to this. */
    public GroupingOperation addHint(String hint) {
        hints.add(hint);
        return this;
    }

    /** Returns whether the given hint has been added to this. */
    public boolean containsHint(String hint) {
        return hints.contains(hint);
    }

    /** Returns an immutable view to the hint list of this node. */
    public Set<String> getHints() {
        return Collections.unmodifiableSet(hints);
    }

    /**
     * Adds a child grouping node to this. This will also set the parent of the child so that it points to this node.
     *
     * @param op the child node to add
     * @return this, to allow chaining
     */
    public GroupingOperation addChild(GroupingOperation op) {
        op.parent = this;
        children.add(op);
        return this;
    }

    /**
     * Convenience method to call {@link #addChild(GroupingOperation)} for each element in the given list.
     *
     * @param lst the list of operations to add
     * @return this, to allow chaining
     */
    public GroupingOperation addChildren(List<GroupingOperation> lst) {
        for (GroupingOperation op : lst) {
            addChild(op);
        }
        return this;
    }

    /** Returns the number of child operations of this. */
    public int getNumChildren() {
        return children.size();
    }

    /**
     * Returns the child operation at the given index.
     *
     * @param i the index of the child to return
     * @return the child at the given index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public GroupingOperation getChild(int i) {
        return children.get(i);
    }

    /** Returns an immutable view to the child list of this node. */
    public List<GroupingOperation> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Assigns an expressions as the group-by clause of this operation.
     *
     * @param exp the expression to assign to this
     * @return this, to allow chaining
     */
    public GroupingOperation setGroupBy(GroupingExpression exp) {
        groupBy = exp;
        return this;
    }

    /** Returns the expression assigned as the group-by clause of this. */
    public GroupingExpression getGroupBy() {
        return groupBy;
    }

    /**
     * Returns the conceptual level of this node.
     *
     * @return the level, or -1 if not resolved
     * @see #resolveLevel(int)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Resolves the conceptual level of this operation. This level represents the type of data that is consumed by this
     * operation, where level 0 is a single hit, level 1 is a group, level 2 is a list of groups, and so forth. This
     * method verifies the input level against the operation type, and recursively resolves the level of all argument
     * expressions.
     *
     * @param level the level of the input data
     * @throws IllegalArgumentException thrown if a contained expression is invalid for the given level
     */
    public void resolveLevel(int level) {
        if (groupBy != null) {
            if (level == 0) {
                throw new IllegalArgumentException("Operation '" + this + "' can not group " +
                                                   getLevelDesc(level) + ".");
            }
            groupBy.resolveLevel(level - 1);
            ++level;
        }
        if (hasMax()) {
            if (level == 0) {
                throw new IllegalArgumentException("Operation '" + this + "' can not apply max to " +
                                                   getLevelDesc(level) + ".");
            }
        }
        this.level = level;
        for (GroupingExpression exp : outputs) {
            exp.resolveLevel(level);
        }
        if (!orderBy.isEmpty()) {
            if (level == 0) {
                throw new IllegalArgumentException("Operation '" + this + "' can not order " +
                                                   getLevelDesc(level) + ".");
            }
            for (GroupingExpression exp : orderBy) {
                exp.resolveLevel(level - 1);
            }
        }
        for (GroupingOperation child : children) {
            child.resolveLevel(level);
        }
    }

    /** Returns the parent of this, or null if none */
    protected GroupingOperation getParent() { return parent; }

    public GroupingOperation setForceSinglePass(boolean forceSinglePass) {
        this.forceSinglePass = forceSinglePass;
        return this;
    }

    public boolean getForceSinglePass() {
        return forceSinglePass;
    }

    /** Sets the maximum number of groups to return for this operation. */
    public GroupingOperation setMax(int max) {
        this.max = max;
        return this;
    }

    /** Returns the max value of this. */
    public int getMax() {
        return max;
    }

    /** Indicates if the 'max' value has been set. */
    public boolean hasMax() { return max >= 0; }

    @Beta public boolean hasUnlimitedMax() { return max == Integer.MAX_VALUE; }

    /**
     * Assigns an accuracy value for this. This is a number between 0 and 1 describing the accuracy of the result, which
     * again determines the speed of the grouping request. A low value will make sure the grouping operation runs fast,
     * at the sacrifice if a (possible) imprecise result.
     *
     * @param accuracy the accuracy to assign to this
     * @return this, to allow chaining
     * @throws IllegalArgumentException if the accuracy is outside the allowed value range
     */
    public GroupingOperation setAccuracy(double accuracy) {
        if (accuracy > 1.0 || accuracy < 0.0) {
            throw new IllegalArgumentException("Illegal accuracy '" + accuracy + "'. Must be between 0 and 1.");
        }
        this.accuracy = accuracy;
        return this;
    }

    /** Return the accuracy of this. */
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * Adds an expression to the order-by clause of this operation.
     *
     * @param exp the expressions to add to this
     * @return this, to allow chaining
     */
    public GroupingOperation addOrderBy(GroupingExpression exp) {
        orderBy.add(exp);
        return this;
    }

    /**
     * Convenience method to call {@link #addOrderBy(GroupingExpression)} for each element in the given list.
     *
     * @param list the list of expressions to add
     * @return this, to allow chaining
     */
    public GroupingOperation addOrderBy(List<GroupingExpression> list) {
        for (GroupingExpression exp : list) {
            addOrderBy(exp);
        }
        return this;
    }

    /**
     * Returns the number of expressions in the order-by clause of this.
     *
     * @return the expression count
     */
    public int getNumOrderBy() {
        return orderBy.size();
    }

    /**
     * Returns the group-by expression at the given index.
     *
     * @param i the index of the expression to return
     * @return the expression at the given index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public GroupingExpression getOrderBy(int i) {
        return orderBy.get(i);
    }

    /**
     * Returns an immutable view to the order-by clause of this.
     *
     * @return the expression list
     */
    public List<GroupingExpression> getOrderBy() {
        return Collections.unmodifiableList(orderBy);
    }

    /**
     * Adds an expression to the output clause of this operation.
     *
     * @param exp the expressions to add to this
     * @return this, to allow chaining
     */
    public GroupingOperation addOutput(GroupingExpression exp) {
        outputs.add(exp);
        return this;
    }

    /**
     * Convenience method to call {@link #addOutput(GroupingExpression)} for each element in the given list.
     *
     * @param lst the list of expressions to add
     * @return this, to allow chaining
     */
    public GroupingOperation addOutputs(List<GroupingExpression> lst) {
        for (GroupingExpression exp : lst) {
            addOutput(exp);
        }
        return this;
    }

    /**
     * Returns the number of expressions in the output clause of this.
     *
     * @return the expression count
     */
    public int getNumOutputs() {
        return outputs.size();
    }

    /**
     * Returns the output expression at the given index.
     *
     * @param i the index of the expression to return
     * @return the expression at the given index
     * @throws IndexOutOfBoundsException If the index is out of range
     */
    public GroupingExpression getOutput(int i) {
        return outputs.get(i);
    }

    /**
     * Returns an immutable view to the output clause of this.
     *
     * @return the expression list
     */
    public List<GroupingExpression> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    /**
     * Assigns the precision clause of this. This is the number of intermediate groups returned from each content node
     * during expression evaluation to give the container node more data to consider when selecting the N groups that are
     * to be evaluated further.
     *
     * @param precision the precision to set
     * @return this, to allow chaining
     * @see #setMax(int)
     */
    public GroupingOperation setPrecision(int precision) {
        this.precision = precision;
        return this;
    }

    /** Returns the precision value of this. */
    public int getPrecision() {
        return precision;
    }

    /**
     * Assigns a string as the where clause of this operation.
     *
     * @param string the string to assign to this
     * @return this, to allow chaining
     */
    public GroupingOperation setWhere(String string) {
        where = string;
        return this;
    }

    /**
     * Returns the where clause assigned to this operation.
     *
     * @return The where clause.
     */
    public String getWhere() {
        return where;
    }

    /**
     * Recursively calls {@link GroupingExpression#visit(ExpressionVisitor)} on all {@link GroupingExpression} objects
     * in this operation and in all of its child operations.
     *
     * @param visitor The visitor to call.
     */
    public void visitExpressions(ExpressionVisitor visitor) {
        for (GroupingExpression exp : aliases.values()) {
            exp.visit(visitor);
        }
        for (GroupingExpression exp : outputs) {
            exp.visit(visitor);
        }
        for (GroupingExpression exp : orderBy) {
            exp.visit(visitor);
        }
        if (groupBy != null) {
            groupBy.visit(visitor);
        }
        for (GroupingOperation op : children) {
            op.visitExpressions(visitor);
        }
    }

    @Override
    public GroupingOperation setLabel(String label) {
        super.setLabel(label);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(super.toString()).append("(");
        if (groupBy != null) {
            ret.append("group(").append(groupBy).append(") ");
        }
        for (String hint : hints) {
            ret.append("hint(").append(hint).append(") ");
        }
        if (hasMax()) {
            ret.append("max(").append(max).append(") ");
        }
        if (!orderBy.isEmpty()) {
            ret.append("order(");
            ret.append(GroupingExpression.asString(orderBy));
            ret.append(") ");
        }
        if (!outputs.isEmpty()) {
            ret.append("output(");
            for (int i = 0, len = outputs.size(); i < len; ++i) {
                GroupingExpression exp = outputs.get(i);
                ret.append(exp);
                String label = exp.getLabel();
                if (label != null) {
                    ret.append(" as(").append(label).append(")");
                }
                if (i < len - 1) {
                    ret.append(", ");
                }
            }
            ret.append(") ");
        }
        if (precision != 0) {
            ret.append("precision(").append(precision).append(") ");
        }
        if (where != null) {
            ret.append("where(").append(where).append(") ");
        }
        for (GroupingOperation child : children) {
            ret.append(child).append(" ");
        }
        int len = ret.length();
        if (ret.charAt(len - 1) == ' ') {
            ret.setLength(len - 1);
        }
        ret.append(")");
        String label = getLabel();
        if (label != null) {
            ret.append(" as(").append(label).append(")");
        }
        return ret.toString();
    }

    /**
     * Returns a description of the given level. This allows for more descriptive errors being passed back to the user.
     *
     * @param level The level to describe.
     * @return A description of the given level.
     */
    public static String getLevelDesc(int level) {
        if (level <= 0) {
            return "single hit";
        } else if (level == 1) {
            return "single group";
        } else {
            StringBuilder ret = new StringBuilder();
            for (int i = 1; i < level; ++i) {
                ret.append("list of ");
            }
            ret.append("groups");
            return ret.toString();
        }
    }

    /**
     * Convenience method to call {@link #fromStringAsList(String)} and assert that the list contains exactly one
     * grouping operation.
     *
     * @param str the string to parse
     * @return a grouping operation that corresponds to the string
     * @throws IllegalArgumentException thrown if the string could not be parsed as a single operation
     */
    public static GroupingOperation fromString(String str) {
        List<GroupingOperation> lst = fromStringAsList(str);
        if (lst.size() != 1) {
            throw new IllegalArgumentException("Expected 1 operation, got " + lst.size() + ".");
        }
        return lst.get(0);
    }

    /**
     * Parses the given string as a list of grouping operations. This method never returns null, it either returns a
     * list of valid grouping requests or it throws an exception.
     *
     * @param string the string to parse
     * @return a list of grouping operations that corresponds to the string
     * @throws IllegalArgumentException thrown if the string could not be parsed
     */
    public static List<GroupingOperation> fromStringAsList(String string) {
        if (string == null || string.trim().length() == 0) {
            return Collections.emptyList();
        }
        GroupingParserInput input = new GroupingParserInput(string);
        try {
            return new GroupingParser(input).requestList();
        } catch (ParseException | TokenMgrException e) {
            throw new IllegalArgumentException(input.formatException(e.getMessage()), e);
        }
    }

}
