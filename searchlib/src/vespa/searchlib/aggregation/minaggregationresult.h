// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

/**
 * Aggregator that keeps the minimum value.
 */
class MinAggregationResult : public AggregationResult {
public:
    using SingleResultNode = expression::SingleResultNode;

private:
    SingleResultNode::CP _min;

public:
    DECLARE_AGGREGATIONRESULT(MinAggregationResult);

    MinAggregationResult();
    explicit MinAggregationResult(const SingleResultNode& min);
    ~MinAggregationResult() override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    [[nodiscard]] const SingleResultNode& getMin() const { return *_min; }

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return getMin(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
};

} // namespace search::aggregation
