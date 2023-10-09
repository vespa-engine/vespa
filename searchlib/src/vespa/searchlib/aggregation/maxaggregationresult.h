// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search::aggregation {

class MaxAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(MaxAggregationResult);
    MaxAggregationResult();
    MaxAggregationResult(const SingleResultNode & max);
    ~MaxAggregationResult();
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const SingleResultNode & getMax() const { return *_max; }
private:
    const ResultNode & onGetRank() const override { return getMax(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    SingleResultNode::CP _max;
};

}
