// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingExpression;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.NegFunction;
import com.yahoo.searchlib.aggregation.*;
import com.yahoo.searchlib.expression.ExpressionNode;

import java.util.*;

/**
 * This class implements the necessary logic to build a list of {@link Grouping} objects from an instance of {@link
 * GroupingOperation}. It is used by the {@link GroupingExecutor}.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
class RequestBuilder {

    private static final int LOOKAHEAD = 1;
    private final ExpressionConverter converter = new ExpressionConverter();
    private final List<Grouping> requestList = new LinkedList<>();
    private final GroupingTransform transform;
    private GroupingOperation root;
    private int tag = 0;

    /**
     * Constructs a new instance of this class.
     *
     * @param requestId The id of the corresponding {@link GroupingRequest}.
     */
    public RequestBuilder(int requestId) {
        this.transform = new GroupingTransform(requestId);
    }

    /**
     * Sets the abstract syntax tree of the request whose back-end queries to create.
     *
     * @param root The grouping request to convert.
     * @return This, to allow chaining.
     */
    public RequestBuilder setRootOperation(GroupingOperation root) {
        root.getClass(); // throws NullPointerException
        this.root = root;
        return this;
    }

    /**
     * Sets the time zone to build the request for. This information is propagated to the time-based grouping
     * expressions so that the produced groups are reasonable for the given zone.
     *
     * @param timeZone The time zone to set.
     * @return This, to allow chaining.
     */
    public RequestBuilder setTimeZone(TimeZone timeZone) {
        converter.setTimeOffset(timeZone != null ? timeZone.getOffset(System.currentTimeMillis())
                                                 : ExpressionConverter.DEFAULT_TIME_OFFSET);
        return this;
    }

    /**
     * Sets the name of the summary class to use if a {@link com.yahoo.search.grouping.request.SummaryValue} has none.
     *
     * @param summaryName The summary class name to set.
     * @return This, to allow chaining.
     */
    public RequestBuilder setDefaultSummaryName(String summaryName) {
        converter.setDefaultSummaryName(summaryName != null ? summaryName
                                                            : ExpressionConverter.DEFAULT_SUMMARY_NAME);
        return this;
    }

    /**
     * Returns the transform that was created when {@link #build()} was called.
     *
     * @return The grouping transform that was built.
     */
    public GroupingTransform getTransform() {
        return transform;
    }

    /**
     * Returns the list of grouping objects that were created when {@link #build()} was called.
     *
     * @return The list of built grouping objects.
     */
    public List<Grouping> getRequestList() {
        return requestList;
    }

    /**
     * Constructs a set of Vespa specific grouping request that corresponds to the parameters given to this builder.
     * This method might fail due to unsupported constructs in the request, in which case an exception is thrown.
     *
     * @throws IllegalStateException         If this method is called more than once.
     * @throws UnsupportedOperationException If the grouping request contains unsupported constructs.
     */
    public void build() {
        if (tag != 0) {
            throw new IllegalStateException();
        }
        root.resolveLevel(1);

        Grouping grouping = new Grouping();
        grouping.getRoot().setTag(++tag);
        grouping.setForceSinglePass(root.getForceSinglePass() || root.containsHint("singlepass"));
        Stack<BuildFrame> stack = new Stack<>();
        stack.push(new BuildFrame(grouping, new BuildState(), root));
        while (!stack.isEmpty()) {
            BuildFrame frame = stack.pop();
            processRequestNode(frame);
            List<GroupingOperation> children = frame.astNode.getChildren();
            if (children.isEmpty()) {
                requestList.add(frame.grouping);
            } else {
                for (int i = children.size(); --i >= 0; ) {
                    Grouping childGrouping = (i == 0) ? frame.grouping : frame.grouping.clone();
                    BuildState childState = (i == 0) ? frame.state : new BuildState(frame.state);
                    BuildFrame child = new BuildFrame(childGrouping, childState, children.get(i));
                    stack.push(child);
                }
            }
        }
        pruneRequests();
    }

    public RequestBuilder addContinuations(Iterable<Continuation> continuations) {
        for (Continuation continuation : continuations) {
            if (continuation == null) {
                continue;
            }
            transform.addContinuation(continuation);
        }
        return this;
    }

    private void processRequestNode(BuildFrame frame) {
        int level = frame.astNode.getLevel();
        if (level > 2) {
            throw new UnsupportedOperationException("Can not operate on " +
                                                    GroupingOperation.getLevelDesc(level) + ".");
        }
        if (frame.astNode instanceof EachOperation) {
            resolveEach(frame);
        } else {
            resolveOutput(frame);
        }
        resolveState(frame);
        injectGroupByToExpressionCountAggregator(frame);
    }

    private void injectGroupByToExpressionCountAggregator(BuildFrame frame) {
        Group group = getLeafGroup(frame);
        // The ExpressionCountAggregationResult uses the group-by expression to simulate aggregation of list of groups.
        group.getAggregationResults().stream()
                .filter(aggr -> aggr instanceof ExpressionCountAggregationResult)
                .forEach(aggr -> aggr.setExpression(frame.state.groupBy.clone()));
    }

    private void resolveEach(BuildFrame frame) {
        int parentTag = getLeafGroup(frame).getTag();
        if (frame.state.groupBy != null) {
            GroupingLevel grpLevel = new GroupingLevel();
            grpLevel.getGroupPrototype().setTag(++tag);
            grpLevel.setExpression(frame.state.groupBy);
            frame.state.groupBy = null;
            int offset = transform.getOffset(tag);
            if (frame.state.precision != null) {
                grpLevel.setPrecision(frame.state.precision + offset);
                frame.state.precision = null;
            }
            if (frame.state.max != null) {
                transform.putMax(tag, frame.state.max, "group list");
                grpLevel.setMaxGroups(LOOKAHEAD + frame.state.max + offset);
                frame.state.max = null;
            }
            frame.grouping.getLevels().add(grpLevel);
        }
        String label = frame.astNode.getLabel();
        if (label != null) {
            frame.state.label = label;
        }
        if (frame.astNode.getLevel() > 0) {
            transform.putLabel(parentTag, getLeafGroup(frame).getTag(), frame.state.label, "group list");
        }
        resolveOutput(frame);
        if (!frame.state.orderByExp.isEmpty()) {
            GroupingLevel grpLevel = getLeafGroupingLevel(frame);
            for (int i = 0, len = frame.state.orderByExp.size(); i < len; ++i) {
                grpLevel.getGroupPrototype().addOrderBy(frame.state.orderByExp.get(i),
                                                        frame.state.orderByAsc.get(i));
            }
            frame.state.orderByExp.clear();
            frame.state.orderByAsc.clear();
        }
    }

    private void resolveState(BuildFrame frame) {
        resolveGroupBy(frame);
        resolveMax(frame);
        resolveOrderBy(frame);
        resolvePrecision(frame);
        resolveWhere(frame);
    }

    private void resolveGroupBy(BuildFrame frame) {
        GroupingExpression exp = frame.astNode.getGroupBy();
        if (exp != null) {
            if (frame.state.groupBy != null) {
                throw new UnsupportedOperationException("Can not group list of groups.");
            }
            frame.state.groupBy = converter.toExpressionNode(exp);
            frame.state.label = exp.toString(); // label for next each()

        } else {
            int level = frame.astNode.getLevel();
            if (level == 0) {
                // no next each()
            } else if (level == 1) {
                frame.state.label = "hits"; // next each() is hitlist
            } else {
                throw new UnsupportedOperationException("Can not create anonymous " +
                                                        GroupingOperation.getLevelDesc(level) + ".");
            }
        }
    }

    private long computeNewTopN(long oldMax, long newMax) {
        return (oldMax < 0) ? newMax : Math.min(oldMax, newMax);
    }
    private void resolveMax(BuildFrame frame) {
        if (frame.astNode.hasMax()) {
            int max = frame.astNode.getMax();
            if (isTopNAllowed(frame)) {
                frame.grouping.setTopN(computeNewTopN(frame.grouping.getTopN(), max));
            } else {
                frame.state.max = max;
            }
        }
    }

    private void resolveOrderBy(BuildFrame frame) {
        List<GroupingExpression> lst = frame.astNode.getOrderBy();
        if (lst == null || lst.isEmpty()) {
            return;
        }
        int reqLevel = frame.astNode.getLevel();
        if (reqLevel != 2) {
            throw new UnsupportedOperationException(
                    "Can not order " + GroupingOperation.getLevelDesc(reqLevel) + " content.");
        }
        for (GroupingExpression exp : lst) {
            boolean asc = true;
            if (exp instanceof NegFunction) {
                asc = false;
                exp = ((NegFunction)exp).getArg(0);
            }
            frame.state.orderByExp.add(converter.toExpressionNode(exp));
            frame.state.orderByAsc.add(asc);
        }
    }

    private void resolveOutput(BuildFrame frame) {
        List<GroupingExpression> lst = frame.astNode.getOutputs();
        if (lst == null || lst.isEmpty()) {
            return;
        }
        Group group = getLeafGroup(frame);
        for (GroupingExpression exp : lst) {
            group.addAggregationResult(toAggregationResult(exp, group, frame));
        }
    }

    private AggregationResult toAggregationResult(GroupingExpression exp, Group group, BuildFrame frame) {
        AggregationResult result = converter.toAggregationResult(exp);
        result.setTag(++tag);

        String label = exp.getLabel();
        if (result instanceof HitsAggregationResult) {
            if (label != null) {
                throw new UnsupportedOperationException("Can not label expression '" + exp + "'.");
            }
            HitsAggregationResult hits = (HitsAggregationResult)result;
            if (frame.state.max != null) {
                transform.putMax(tag, frame.state.max, "hit list");
                int offset = transform.getOffset(tag);
                hits.setMaxHits(LOOKAHEAD + frame.state.max + offset);
                frame.state.max = null;
            }
            transform.putLabel(group.getTag(), tag, frame.state.label, "hit list");
        } else {
            transform.putLabel(group.getTag(), tag, label != null ? label : exp.toString(), "output");
        }
        return result;
    }

    private void resolvePrecision(BuildFrame frame) {
        int precision = frame.astNode.getPrecision();
        if (precision > 0) {
            frame.state.precision = precision;
        }
    }

    private void resolveWhere(BuildFrame frame) {
        String where = frame.astNode.getWhere();
        if (where != null) {
            if (!isRootOperation(frame)) {
                throw new UnsupportedOperationException("Can not apply 'where' to non-root group.");
            }
            switch (where) {
            case "true":
                frame.grouping.setAll(true);
                break;
            case "$query":
                // ignore
                break;
            default:
                throw new UnsupportedOperationException("Operation 'where' does not support '" + where + "'.");
            }
        }
    }

    private boolean isRootOperation(BuildFrame frame) {
        return frame.astNode == root && frame.state.groupBy == null;
    }

    private boolean isTopNAllowed(BuildFrame frame) {
        return (frame.astNode instanceof AllOperation) && (frame.state.groupBy == null);
    }

    private GroupingLevel getLeafGroupingLevel(BuildFrame frame) {
        if (frame.grouping.getLevels().isEmpty()) {
            return null;
        }
        return frame.grouping.getLevels().get(frame.grouping.getLevels().size() - 1);
    }

    private Group getLeafGroup(BuildFrame frame) {
        if (frame.grouping.getLevels().isEmpty()) {
            return frame.grouping.getRoot();
        } else {
            GroupingLevel grpLevel = getLeafGroupingLevel(frame);
            return grpLevel != null ? grpLevel.getGroupPrototype() : null;
        }
    }

    private void pruneRequests() {
        for (int reqIdx = requestList.size(); --reqIdx >= 0; ) {
            Grouping request = requestList.get(reqIdx);
            List<GroupingLevel> lst = request.getLevels();
            for (int lvlIdx = lst.size(); --lvlIdx >= 0; ) {
                if (!lst.get(lvlIdx).getGroupPrototype().getAggregationResults().isEmpty()) {
                    break;
                }
                lst.remove(lvlIdx);
            }
            if (lst.isEmpty() && request.getRoot().getAggregationResults().isEmpty()) {
                requestList.remove(reqIdx);
            }
        }
    }

    private static class BuildFrame {

        final Grouping grouping;
        final BuildState state;
        final GroupingOperation astNode;

        BuildFrame(Grouping grouping, BuildState state, GroupingOperation astNode) {
            this.grouping = grouping;
            this.state = state;
            this.astNode = astNode;
        }
    }

    private static class BuildState {

        final List<ExpressionNode> orderByExp = new ArrayList<>();
        final List<Boolean> orderByAsc = new ArrayList<>();
        ExpressionNode groupBy = null;
        String label = null;
        Integer max = null;
        Integer precision = null;

        BuildState() {
            // empty
        }

        BuildState(BuildState obj) {
            for (ExpressionNode e : obj.orderByExp) {
                orderByExp.add(e.clone());
            }
            orderByAsc.addAll(obj.orderByAsc);
            groupBy = obj.groupBy;
            label = obj.label;
            max = obj.max;
            precision = obj.precision;
        }
    }
}
