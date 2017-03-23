// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>


namespace search {
namespace aggregation {

class SumAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(SumAggregationResult);
    SumAggregationResult() : AggregationResult(), _sum() { }
    SumAggregationResult(SingleResultNode::UP sum) : AggregationResult(), _sum(sum.release()) { }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    const SingleResultNode & getSum() const { return *_sum; }
private:
    virtual const ResultNode & onGetRank() const { return getSum(); }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    SingleResultNode::CP _sum;
};

}
}
