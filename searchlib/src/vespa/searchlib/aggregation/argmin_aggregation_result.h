// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

/**
 * Aggregator selecting a value based on the minimum of another expression: argmin(key, value)
 * keeps the value of the hit in the group that has the smallest key.
 */
class ArgminAggregationResult : public AggregationResult {
public:
    using SingleResultNode = expression::SingleResultNode;
    using ExpressionTree = expression::ExpressionTree;

private:
    // Shared, not deep-copied, for the same reason as AggregationResult::_expressionTree:
    // a copy and its original must keep resolving to the same prepared tree.
    std::shared_ptr<ExpressionTree> _key_tree;
    SingleResultNode::CP            _key;         // key of the winning hit
    SingleResultNode::CP            _scratch_key; // candidate key, not serialized
    SingleResultNode::CP            _value;       // value of the winning hit
    bool                            _has_value;

public:
    DECLARE_AGGREGATIONRESULT(ArgminAggregationResult);

    ArgminAggregationResult();
    ArgminAggregationResult(const SingleResultNode& key, const SingleResultNode& value);
    ~ArgminAggregationResult() override;

    ArgminAggregationResult& set_key_expression(ExpressionNode::UP key);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;

    [[nodiscard]] const SingleResultNode& key() const noexcept { return *_key; }
    [[nodiscard]] const SingleResultNode& value() const noexcept { return *_value; }
    [[nodiscard]] bool has_value() const noexcept { return _has_value; }

private:
    [[nodiscard]] bool fill_scratch_key();
    void update_value(const ResultNode& result);

    const ResultNode& onGetRank() const override { return value(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
    void onAggregate(const ResultNode& result, DocId docId, HitRank rank) override;
    void onAggregate(const ResultNode& result, const document::Document& doc, HitRank rank) override;
};

} // namespace search::aggregation
