// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/aggregation/aggregationresult.h>
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

/**
 * Aggregator that keeps the value of the sub expression for the first hit in the group.
 */
class FirstAggregationResult : public AggregationResult {
    expression::SingleResultNode::CP _first;
    HitRank                          _hit_rank;
    bool                             _has_value;

public:
    using SingleResultNode = expression::SingleResultNode;

    DECLARE_AGGREGATIONRESULT(FirstAggregationResult);

    FirstAggregationResult();
    explicit FirstAggregationResult(const SingleResultNode& first, HitRank hit_rank = default_rank_value);
    ~FirstAggregationResult() override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;

    [[nodiscard]] const SingleResultNode& first() const noexcept { return *_first; }
    [[nodiscard]] HitRank hit_rank() const noexcept { return _hit_rank; }
    [[nodiscard]] bool has_value() const noexcept { return _has_value; }

private:
    void update_first(const ResultNode& result, HitRank rank);

    const ResultNode& onGetRank() const override { return first(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
    void onAggregate(const ResultNode& result, DocId docId, HitRank rank) override;
    void onAggregate(const ResultNode& result, const document::Document& doc, HitRank rank) override;
};

} // namespace search::aggregation
