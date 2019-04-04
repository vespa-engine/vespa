// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

class SumAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(SumAggregationResult);
    SumAggregationResult();
    SumAggregationResult(SingleResultNode::UP sum);
    ~SumAggregationResult() override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const SingleResultNode & getSum() const { return *_sum; }
private:
    const ResultNode & onGetRank() const override { return getSum(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    SingleResultNode::CP _sum;
};

}
