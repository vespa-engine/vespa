// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/numericresultnode.h>

namespace search::aggregation {

class SumAggregationResult : public AggregationResult
{
public:
    using NumericResultNode = expression::NumericResultNode;
    DECLARE_AGGREGATIONRESULT(SumAggregationResult);
    SumAggregationResult();
    SumAggregationResult(NumericResultNode::UP sum);
    ~SumAggregationResult() override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const NumericResultNode & getSum() const { return *_sum; }
private:
    const ResultNode & onGetRank() const override { return getSum(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    NumericResultNode::CP _sum;
};

}
