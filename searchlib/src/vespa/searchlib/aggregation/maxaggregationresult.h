// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

/**
 * Aggregator that keeps the maximum value.
 */
class MaxAggregationResult : public AggregationResult {
public:
    using SingleResultNode = expression::SingleResultNode;

private:
    SingleResultNode::CP _max;

public:
    DECLARE_AGGREGATIONRESULT(MaxAggregationResult);

    MaxAggregationResult();
    explicit MaxAggregationResult(const SingleResultNode& max);
    ~MaxAggregationResult() override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    [[nodiscard]] const SingleResultNode& getMax() const { return *_max; }

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return getMax(); }
    void onPrepare(const ResultNode& result) override;
    void initForUnitTest(const ResultNode& result) override;
};

} // namespace search::aggregation
