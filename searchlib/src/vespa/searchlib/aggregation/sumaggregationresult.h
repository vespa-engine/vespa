// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/numericresultnode.h>

namespace search::aggregation {

/**
 * Aggregator that calculates the sum of the sub expression for each hit.
 */
class SumAggregationResult : public AggregationResult {
public:
    using NumericResultNode = expression::NumericResultNode;

private:
    NumericResultNode::CP _sum;

public:
    DECLARE_AGGREGATIONRESULT(SumAggregationResult);
    SumAggregationResult();
    explicit SumAggregationResult(NumericResultNode::UP sum);
    ~SumAggregationResult() override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    [[nodiscard]] const NumericResultNode& getSum() const { return *_sum; }

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return getSum(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
};

} // namespace search::aggregation
