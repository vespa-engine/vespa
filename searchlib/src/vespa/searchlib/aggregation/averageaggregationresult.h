// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/numericresultnode.h>


namespace search {
namespace aggregation {

class AverageAggregationResult : public AggregationResult
{
public:
    using NumericResultNode = expression::NumericResultNode;
    DECLARE_AGGREGATIONRESULT(AverageAggregationResult);
    AverageAggregationResult() : _sum(), _count(0) {}
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    const NumericResultNode & getAverage() const;
    const NumericResultNode & getSum() const { return *_sum; }
    uint64_t getCount()                const { return _count; }
private:
    virtual const ResultNode & onGetRank() const { return getAverage(); }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    NumericResultNode::CP _sum;
    uint64_t              _count;
    mutable NumericResultNode::CP _averageScratchPad;
};

}
}
