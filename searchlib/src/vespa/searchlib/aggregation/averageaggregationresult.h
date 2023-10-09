// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/numericresultnode.h>

namespace search::aggregation {

class AverageAggregationResult : public AggregationResult
{
public:
    using NumericResultNode = expression::NumericResultNode;
    DECLARE_AGGREGATIONRESULT(AverageAggregationResult);
    AverageAggregationResult() : _sum(), _count(0) {}
    ~AverageAggregationResult() override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const NumericResultNode & getAverage() const;
    const NumericResultNode & getSum() const { return *_sum; }
    uint64_t getCount()                const { return _count; }
private:
    const ResultNode & onGetRank() const override { return getAverage(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    NumericResultNode::CP _sum;
    uint64_t              _count;
    mutable NumericResultNode::CP _averageScratchPad;
};

}
