// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

/**
 * Aggregator that keeps the value of the main expression for the first hit in the group.
 *
 * Which hit is first is decided by an ordering key, not by the order hits happen to be
 * delivered in: hits stream through the aggregator in docid order when the grouping needs a
 * full resort (any order() clause) and in the query's sort order otherwise, and no delivery
 * order exists at all across the nodes whose partial results are merged. The key of the
 * winning hit is therefore part of the result, so that aggregation and merging pick the same
 * hit no matter what order they see the candidates in.
 *
 * With a key expression set, the first hit is the one with the smallest key, matching the
 * ascending default of order(); negate the key to select by descending order. Without one,
 * the key is the negated hit rank, so the first hit is the highest ranked one. Ties keep the
 * candidate seen first, which is unspecified across nodes. Multi-value key expressions are
 * not supported; the first element is used.
 */
class FirstAggregationResult : public AggregationResult {
public:
    using SingleResultNode = expression::SingleResultNode;
    using ExpressionTree = expression::ExpressionTree;

private:
    // Shared, not deep-copied, for the same reason as AggregationResult::_expressionTree:
    // a copy and its original must keep resolving to the same prepared tree.
    std::shared_ptr<ExpressionTree> _key_tree;
    SingleResultNode::CP            _key;         // key of the winning hit
    SingleResultNode::CP            _scratch_key; // candidate key, not serialized
    SingleResultNode::CP            _first;       // value of the winning hit
    bool                            _has_value;

public:
    DECLARE_AGGREGATIONRESULT(FirstAggregationResult);

    FirstAggregationResult();
    FirstAggregationResult(const SingleResultNode& first, const SingleResultNode& key);
    ~FirstAggregationResult() override;

    FirstAggregationResult& set_key_expression(ExpressionNode::UP key);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;

    [[nodiscard]] const SingleResultNode& first() const noexcept { return *_first; }
    [[nodiscard]] const SingleResultNode& key() const noexcept { return *_key; }
    [[nodiscard]] bool has_value() const noexcept { return _has_value; }

private:
    [[nodiscard]] bool fill_scratch_key(HitRank rank);
    void update_first(const ResultNode& result);

    const ResultNode& onGetRank() const override { return first(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
    void onAggregate(const ResultNode& result, DocId docId, HitRank rank) override;
    void onAggregate(const ResultNode& result, const document::Document& doc, HitRank rank) override;
};

} // namespace search::aggregation
