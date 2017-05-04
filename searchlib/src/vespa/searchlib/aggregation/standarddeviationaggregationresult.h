// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>


namespace search::aggregation {

// Aggregator that calculates the population standard deviation
class StandardDeviationAggregationResult : public AggregationResult
{
public:
    DECLARE_AGGREGATIONRESULT(StandardDeviationAggregationResult);
    StandardDeviationAggregationResult() : AggregationResult(), _count(), _sum(), _sumOfSquared(), _stdDevScratchPad()
    {
        _stdDevScratchPad.reset(new expression::FloatResultNode());
    }

    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const double getSum() const noexcept { return _sum.getFloat(); }
    const double getSumOfSquared() const noexcept { return _sumOfSquared.getFloat(); }
    const uint64_t getCount() const noexcept { return _count; }
private:
    virtual const ResultNode& onGetRank() const noexcept override { return getStandardDeviation(); }
    virtual void onPrepare(const ResultNode&, bool) override { };
    const expression::NumericResultNode& getStandardDeviation() const noexcept;

    uint64_t _count;
    expression::FloatResultNode _sum;
    expression::FloatResultNode _sumOfSquared;
    mutable expression::FloatResultNode::CP _stdDevScratchPad;
};

}

