// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

class MinAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(MinAggregationResult);
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const SingleResultNode & getMin() const { return *_min; }
    MinAggregationResult();
    MinAggregationResult(const ResultNode::CP &result);
    ~MinAggregationResult();
private:
    const ResultNode & onGetRank() const override { return getMin(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    SingleResultNode::CP _min;
};

}
