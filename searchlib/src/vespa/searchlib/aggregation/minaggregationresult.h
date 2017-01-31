// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>

namespace search {
namespace aggregation {

class MinAggregationResult : public AggregationResult
{
public:
    using SingleResultNode = expression::SingleResultNode;
    DECLARE_AGGREGATIONRESULT(MinAggregationResult);
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    const SingleResultNode & getMin() const { return *_min; }
private:
    virtual const ResultNode & onGetRank() const { return getMin(); }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    SingleResultNode::CP _min;
};

}
}
