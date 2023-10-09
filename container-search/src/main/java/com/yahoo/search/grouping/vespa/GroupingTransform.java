// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class contains enough information about how a {@link com.yahoo.search.grouping.request.GroupingOperation} was
 * transformed into a list {@link com.yahoo.searchlib.aggregation.Grouping} objects, so that the results of those
 * queries can be transformed into something that corresponds to the original request.
 *
 * @author Simon Thoresen Hult
 */
class GroupingTransform {

    private final Map<Integer, Set<Integer>> children = new HashMap<>();
    private final Map<Integer, String> labels = new HashMap<>();
    private final Map<Integer, Integer> maxes = new HashMap<>();
    private final Map<Integer, Integer> offsetByTag = new HashMap<>();
    private final Map<ResultId, Integer> offsetById = new HashMap<>();
    private final Set<ResultId> unstable = new HashSet<>();
    private final int requestId;

    public GroupingTransform(int requestId) {
        this.requestId = requestId;
    }

    public GroupingTransform addContinuation(Continuation cont) {
        if (cont instanceof CompositeContinuation) {
            for (Continuation item : ((CompositeContinuation)cont)) {
                addContinuation(item);
            }
        } else if (cont instanceof OffsetContinuation) {
            OffsetContinuation offsetCont = (OffsetContinuation)cont;
            ResultId id = offsetCont.getResultId();
            if (!id.startsWith(requestId)) {
                return this;
            }
            if (offsetCont.testFlag(OffsetContinuation.FLAG_UNSTABLE)) {
                unstable.add(id);
            } else {
                unstable.remove(id);
            }
            int tag = offsetCont.getTag();
            int offset = offsetCont.getOffset();
            if (getOffset(tag) < offset) {
                offsetByTag.put(tag, offset);
            }
            offsetById.put(id, offset);
        } else {
            throw new UnsupportedOperationException(cont.getClass().getName());
        }
        return this;
    }

    public boolean isStable(ResultId resultId) {
        return !unstable.contains(resultId);
    }

    public int getOffset(int tag) {
        return toPosInt(offsetByTag.get(tag));
    }

    public int getOffset(ResultId resultId) {
        return toPosInt(offsetById.get(resultId));
    }

    public GroupingTransform putMax(int tag, int max, String type) {
        if (maxes.containsKey(tag)) {
            throw new IllegalStateException("Can not set max of " + type + " " + tag + " to " + max +
                                            " because it is already set to " + maxes.get(tag) + ".");
        }
        maxes.put(tag, max);
        return this;
    }

    public int getMax(int tag) {
        return toPosInt(maxes.get(tag));
    }

    public GroupingTransform putLabel(int parentTag, int tag, String label, String type) {
        Set<Integer> siblings = children.get(parentTag);
        if (siblings == null) {
            siblings = new HashSet<>();
            children.put(parentTag, siblings);
        } else {
            for (Integer sibling : siblings) {
                if (label.equals(labels.get(sibling))) {
                    throw new UnsupportedOperationException("Can not use " + type + " label '" + label +
                                                            "' for multiple siblings.");
                }
            }
        }
        siblings.add(tag);
        if (labels.containsKey(tag)) {
            throw new IllegalStateException("Can not set label of " + type + " " + tag + " to '" + label +
                                            "' because it is already set to '" + labels.get(tag) + "'.");
        }
        labels.put(tag, label);
        return this;
    }

    public String getLabel(int tag) {
        return labels.get(tag);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("groupingTransform {\n");
        ret.append("\tlabels {\n");
        for (Map.Entry<Integer, String> entry : labels.entrySet()) {
            ret.append("\t\t").append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }
        ret.append("\t}\n");
        ret.append("\toffsets {\n");
        for (Map.Entry<Integer, Integer> entry : offsetByTag.entrySet()) {
            ret.append("\t\t").append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }
        ret.append("\t}\n");
        ret.append("\tmaxes {\n");
        for (Map.Entry<Integer, Integer> entry : maxes.entrySet()) {
            ret.append("\t\t").append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }
        ret.append("\t}\n");
        ret.append("}");
        return ret.toString();
    }

    private static int toPosInt(Integer val) {
        return val == null ? 0 : Math.max(0, val.intValue());
    }
}
