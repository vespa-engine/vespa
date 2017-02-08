// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search {
namespace aggregation {

class MaxAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(MaxAggregationResult);
    MaxAggregationResult() : AggregationResult(), _max() { }
    MaxAggregationResult(const SingleResultNode & max) : AggregationResult(), _max(max) { }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    const SingleResultNode & getMax() const { return *_max; }
private:
    virtual const ResultNode & onGetRank() const { return getMax(); }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    SingleResultNode::CP _max;
};

}
}
