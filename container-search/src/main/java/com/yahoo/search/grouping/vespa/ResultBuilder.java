// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.result.BoolId;
import com.yahoo.search.grouping.result.DoubleBucketId;
import com.yahoo.search.grouping.result.DoubleId;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupId;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.grouping.result.LongBucketId;
import com.yahoo.search.grouping.result.LongId;
import com.yahoo.search.grouping.result.NullId;
import com.yahoo.search.grouping.result.RawBucketId;
import com.yahoo.search.grouping.result.RawId;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.StringBucketId;
import com.yahoo.search.grouping.result.StringId;
import com.yahoo.search.result.Relevance;
import com.yahoo.searchlib.aggregation.AggregationResult;
import com.yahoo.searchlib.aggregation.AverageAggregationResult;
import com.yahoo.searchlib.aggregation.CountAggregationResult;
import com.yahoo.searchlib.aggregation.ExpressionCountAggregationResult;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.searchlib.aggregation.Hit;
import com.yahoo.searchlib.aggregation.HitsAggregationResult;
import com.yahoo.searchlib.aggregation.MaxAggregationResult;
import com.yahoo.searchlib.aggregation.MinAggregationResult;
import com.yahoo.searchlib.aggregation.StandardDeviationAggregationResult;
import com.yahoo.searchlib.aggregation.SumAggregationResult;
import com.yahoo.searchlib.aggregation.XorAggregationResult;
import com.yahoo.searchlib.expression.BoolResultNode;
import com.yahoo.searchlib.expression.ExpressionNode;
import com.yahoo.searchlib.expression.FloatBucketResultNode;
import com.yahoo.searchlib.expression.FloatResultNode;
import com.yahoo.searchlib.expression.IntegerBucketResultNode;
import com.yahoo.searchlib.expression.IntegerResultNode;
import com.yahoo.searchlib.expression.NullResultNode;
import com.yahoo.searchlib.expression.RawBucketResultNode;
import com.yahoo.searchlib.expression.RawResultNode;
import com.yahoo.searchlib.expression.ResultNode;
import com.yahoo.searchlib.expression.StringBucketResultNode;
import com.yahoo.searchlib.expression.StringResultNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class implements the necessary logic to build a {@link RootGroup} from a list of {@link Grouping} objects. It is
 * used by the {@link GroupingExecutor}.
 *
 * @author Simon Thoresen Hult
 */
class ResultBuilder {

    private final CompositeContinuation continuation = new CompositeContinuation();
    private RootGroup root;
    private GroupListBuilder rootBuilder;
    private HitConverter hitConverter;
    private GroupingTransform transform;

    /**
     * Sets the id of the {@link GroupingRequest} that this builder is creating the result for.
     *
     * @param requestId the id of the corresponding GroupingRequest
     * @return this, to allow chaining
     */
    public ResultBuilder setRequestId(int requestId) {
        root = new RootGroup(requestId, continuation);
        rootBuilder = new GroupListBuilder(ResultId.valueOf(requestId), 0, true, true);
        return this;
    }

    /**
     * Sets the transform that details how the result should be built.
     *
     * @param transform the transform to set
     * @return this, to allow chaining
     */
    public ResultBuilder setTransform(GroupingTransform transform) {
        this.transform = transform;
        return this;
    }

    /**
     * Sets the converts that details how hits are converted.
     *
     * @param hitConverter the converter to set
     * @return this, to allow chaining
     */
    public ResultBuilder setHitConverter(HitConverter hitConverter) {
        this.hitConverter = hitConverter;
        return this;
    }

    /**
     * Adds a grouping result to this transform. This method will recurse through the given object and retrieve all the
     * information it needs to produce the desired result when calling {@link #build()}.
     *
     * @param executionResult the grouping result to process
     */
    public void addGroupingResult(Grouping executionResult) {
        executionResult.unifyNull();
        rootBuilder.addGroup(executionResult.getRoot());
    }

    /**
     * Returns the root {@link RootGroup} that was created when {@link #build()} was called.
     *
     * @return the root that was built
     */
    public RootGroup getRoot() {
        return root;
    }

    /**
     * Returns the {@link Continuation} that would recreate the exact same result as this. It is not complete until
     * {@link #build()} has been called.
     *
     * @return The continuation of this result.
     */
    public Continuation getContinuation() {
        return continuation;
    }

    /**
     * Constructs the grouping result tree that corresponds to the parameters given to this builder. This method might
     * fail due to unsupported constructs in the results, in which case an exception is thrown.
     *
     * @throws UnsupportedOperationException Thrown if the grouping result contains unsupported constructs.
     */
    public void build() {
        int numChildren = rootBuilder.childGroups.size();
        if (numChildren != 1) {
            throw new UnsupportedOperationException("Expected 1 group, got " + numChildren + ".");
        }
        rootBuilder.childGroups.get(0).fill(root);
    }

    private class GroupBuilder {
        private static final int CHILDLIST_SIZE_INCREMENTS = 4;
        boolean [] results = new boolean[8];
        GroupListBuilder [] childLists;
        int childCount = 0;
        final ResultId resultId;
        final com.yahoo.searchlib.aggregation.Group group;
        final boolean stable;

        GroupBuilder(ResultId resultId, com.yahoo.searchlib.aggregation.Group group, boolean stable) {
            this.resultId = resultId;
            this.group = group;
            this.stable = stable;
        }

        Group build(double relevance) {
            return fill(new Group(newGroupId(group), new Relevance(relevance)));
        }

        Group fill(Group group) {
            for (AggregationResult result : this.group.getAggregationResults()) {
                int tag = result.getTag();
                if (result instanceof HitsAggregationResult) {
                    group.add(newHitList(group.size(), tag, (HitsAggregationResult)result));
                } else {
                    String label = transform.getLabel(result.getTag());
                    if (label != null) {
                        group.setField(label, newResult(result, tag));
                    }
                }
            }
            if (childLists != null) {
                for (GroupListBuilder child : childLists) {
                    if (child != null) {
                        group.add(child.build());
                    }
                }
            }
            return group;
        }

        GroupListBuilder getOrCreateChildList(int tag, boolean ranked) {
            int index = tag + 1; // Add 1 to avoid the dreaded -1 default value.
            if (childLists == null || index >= childLists.length) {
                int minSize = index + 1;
                int reservedSize = ((minSize + (CHILDLIST_SIZE_INCREMENTS - 1))/CHILDLIST_SIZE_INCREMENTS) * CHILDLIST_SIZE_INCREMENTS;
                childLists = (childLists == null)
                        ? new GroupListBuilder[reservedSize]
                        : Arrays.copyOf(childLists, reservedSize);
            }
            GroupListBuilder ret = childLists[index];
            if (ret == null) {
                ret = new GroupListBuilder(resultId.newChildId(childCount), tag, stable, ranked);
                childLists[index] = ret;
                childCount++;
            }
            return ret;
        }

        void merge(com.yahoo.searchlib.aggregation.Group group) {
            for (AggregationResult res : group.getAggregationResults()) {
                int tag = res.getTag() + 1; // Add 1 due to dreaded -1 initialization as default.
                if (tag >= results.length) {
                    results = Arrays.copyOf(results, tag+8);
                }
                if ( ! results[tag] ) {
                    this.group.addAggregationResult(res);
                    results[tag] = true;
                }
            }
        }

        GroupId newGroupId(com.yahoo.searchlib.aggregation.Group execGroup) {
            ResultNode res = execGroup.getId();
            if (res instanceof FloatResultNode) {
                return new DoubleId(res.getFloat());
            } else if (res instanceof IntegerResultNode) {
                return new LongId(res.getInteger());
            } else if (res instanceof BoolResultNode) {
                return new BoolId(((BoolResultNode)res).getValue());
            } else if (res instanceof NullResultNode) {
                return new NullId();
            } else if (res instanceof RawResultNode) {
                return new RawId(res.getRaw());
            } else if (res instanceof StringResultNode) {
                return new StringId(res.getString());
            } else if (res instanceof FloatBucketResultNode) {
                FloatBucketResultNode bucketId = (FloatBucketResultNode)res;
                return new DoubleBucketId(bucketId.getFrom(), bucketId.getTo());
            } else if (res instanceof IntegerBucketResultNode) {
                IntegerBucketResultNode bucketId = (IntegerBucketResultNode)res;
                return new LongBucketId(bucketId.getFrom(), bucketId.getTo());
            } else if (res instanceof StringBucketResultNode) {
                StringBucketResultNode bucketId = (StringBucketResultNode)res;
                return new StringBucketId(bucketId.getFrom(), bucketId.getTo());
            } else if (res instanceof RawBucketResultNode) {
                RawBucketResultNode bucketId = (RawBucketResultNode)res;
                return new RawBucketId(bucketId.getFrom(), bucketId.getTo());
            } else {
                throw new UnsupportedOperationException(res.getClass().getName());
            }
        }

        Object newResult(ExpressionNode execResult, int tag) {
            if (execResult instanceof AverageAggregationResult) {
                return ((AverageAggregationResult)execResult).getAverage().getNumber();
            } else if (execResult instanceof CountAggregationResult) {
                return ((CountAggregationResult)execResult).getCount();
            } else if (execResult instanceof ExpressionCountAggregationResult) {
                long count = ((ExpressionCountAggregationResult)execResult).getEstimatedUniqueCount();
                return correctExpressionCountEstimate(count, tag);
            } else if (execResult instanceof MaxAggregationResult) {
                return ((MaxAggregationResult)execResult).getMax().getValue();
            } else if (execResult instanceof MinAggregationResult) {
                return ((MinAggregationResult)execResult).getMin().getValue();
            } else if (execResult instanceof SumAggregationResult) {
                return ((SumAggregationResult) execResult).getSum().getValue();
            } else if (execResult instanceof StandardDeviationAggregationResult) {
                return ((StandardDeviationAggregationResult) execResult).getStandardDeviation();
            } else if (execResult instanceof XorAggregationResult) {
                return ((XorAggregationResult)execResult).getXor();
            } else {
                throw new UnsupportedOperationException(execResult.getClass().getName());
            }
        }

        private long correctExpressionCountEstimate(long count, int tag) {
            int actualGroupCount = group.getNumChildren();
            // Use actual group count if estimate differ. If max is present, only use actual group count if less than max.
            // NOTE: If the actual group count is 0, estimate is also 0.
            if (actualGroupCount > 0 && count != actualGroupCount) {
                if (transform.getMax(tag + 1) == 0 || transform.getMax(tag + 1) > actualGroupCount) {
                    return actualGroupCount;
                }
            }
            return count;
        }

        HitList newHitList(int listIdx, int tag, HitsAggregationResult execResult) {
            HitList hitList = new HitList(transform.getLabel(tag));
            List<Hit> hits = execResult.getHits();
            PageInfo page = new PageInfo(resultId.newChildId(listIdx), tag, stable, hits.size());
            for (int i = page.firstEntry; i < page.lastEntry; ++i) {
                hitList.add(hitConverter.toSearchHit(execResult.getSummaryClass(), hits.get(i)));
            }
            page.putContinuations(hitList.continuations());
            return hitList;
        }

    }

    private class GroupListBuilder {

        final Map<ResultNode, GroupBuilder> childResultGroups = new HashMap<>();
        final List<GroupBuilder> childGroups = new ArrayList<>();
        final ResultId resultId;
        final int tag;
        final boolean stable;
        final boolean stableChildren;
        final boolean ranked;

        GroupListBuilder(ResultId resultId, int tag, boolean stable, boolean ranked) {
            this.resultId = resultId;
            this.tag = tag;
            this.stable = stable;
            this.stableChildren = stable && transform.isStable(resultId);
            this.ranked = ranked;
        }

        GroupList build() {
            PageInfo page = new PageInfo(resultId, tag, stable, childGroups.size());
            GroupList groupList = new GroupList(transform.getLabel(tag));
            for (int i = page.firstEntry; i < page.lastEntry; ++i) {
                GroupBuilder child = childGroups.get(i);
                groupList.add(child.build(ranked ? child.group.getRank() :
                                          (double)(page.lastEntry - i) / (page.lastEntry - page.firstEntry)));
            }
            page.putContinuations(groupList.continuations());
            return groupList;
        }

        void addGroup(com.yahoo.searchlib.aggregation.Group execGroup) {
            GroupBuilder groupBuilder = getOrCreateGroup(execGroup);
            if (execGroup.getNumChildren() > 0) {
                execGroup.sortChildrenByRank();
                List<com.yahoo.searchlib.aggregation.Group> children = execGroup.getChildren();
                boolean ranked = children.get(0).isRankedByRelevance();
                for (com.yahoo.searchlib.aggregation.Group childGroup : children) {
                    GroupListBuilder childList = groupBuilder.getOrCreateChildList(childGroup.getTag(), ranked);
                    childList.addGroup(childGroup);
                }
            }
        }

        GroupBuilder getOrCreateGroup(com.yahoo.searchlib.aggregation.Group execGroup) {
            ResultNode result = execGroup.getId();
            GroupBuilder ret = childResultGroups.get(result);
            if (ret != null) {
                ret.merge(execGroup);
            } else {
                ret = new GroupBuilder(resultId.newChildId(childResultGroups.size()), execGroup, stableChildren);
                childResultGroups.put(result, ret);
                childGroups.add(ret);
            }
            return ret;
        }

    }

    private class PageInfo {

        final ResultId resultId;
        final int tag;
        final int max;
        final int numEntries;
        final int firstEntry;
        final int lastEntry;

        PageInfo(ResultId resultId, int tag, boolean stable, int numEntries) {
            this.resultId = resultId;
            this.tag = tag;
            this.numEntries = numEntries;
            max = transform.getMax(tag);
            if (max > 0) {
                firstEntry = stable ? transform.getOffset(resultId) : 0;
                lastEntry = Math.min(numEntries, firstEntry + max);
            } else {
                firstEntry = 0;
                lastEntry = numEntries;
            }
        }

        void putContinuations(Map<String, Continuation> out) {
            if (max > 0) {
                if (firstEntry > 0) {
                    continuation.add(new OffsetContinuation(resultId, tag, firstEntry, 0));

                    int prevPage = Math.max(0, Math.min(firstEntry, lastEntry) - max);
                    out.put(Continuation.PREV_PAGE, new OffsetContinuation(resultId, tag, prevPage,
                                                                           OffsetContinuation.FLAG_UNSTABLE));
                }
                if (lastEntry < numEntries) {
                    out.put(Continuation.NEXT_PAGE, new OffsetContinuation(resultId, tag, lastEntry,
                                                                           OffsetContinuation.FLAG_UNSTABLE));
                }
            }
        }

    }

    /**
     * Defines a helper interface to convert Vespa style grouping hits into corresponding instances of {@link Hit}.
     * It is an interface to simplify testing.
     */
    public interface HitConverter {

        com.yahoo.search.result.Hit toSearchHit(String summaryClass, com.yahoo.searchlib.aggregation.Hit hit);

    }

}
