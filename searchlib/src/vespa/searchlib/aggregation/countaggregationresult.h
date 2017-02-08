// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/integerresultnode.h>


namespace search {
namespace aggregation {

class CountAggregationResult : public AggregationResult
{
public:
    DECLARE_AGGREGATIONRESULT(CountAggregationResult);
    CountAggregationResult() : AggregationResult(), _count(0) { }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    uint64_t getCount() const { return _count.get(); }
    CountAggregationResult &setCount(uint64_t c) {
        _count = c;
        return *this;
    }
private:
    virtual const ResultNode & onGetRank() const { return _count; }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    expression::Int64ResultNode _count;
};

}
}
