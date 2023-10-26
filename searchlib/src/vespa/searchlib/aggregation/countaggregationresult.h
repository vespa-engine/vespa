// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/integerresultnode.h>

namespace search::aggregation {

class CountAggregationResult : public AggregationResult
{
public:
    DECLARE_AGGREGATIONRESULT(CountAggregationResult);
    CountAggregationResult(uint64_t count=0) : AggregationResult(), _count(count) { }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    uint64_t getCount() const { return _count.get(); }
    CountAggregationResult &setCount(uint64_t c) {
        _count = c;
        return *this;
    }
private:
    const ResultNode & onGetRank() const override { return _count; }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    expression::Int64ResultNode _count;
};

}
