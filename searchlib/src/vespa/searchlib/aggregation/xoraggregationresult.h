// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/integerresultnode.h>

namespace search::aggregation {

class XorAggregationResult : public AggregationResult
{
public:
    using Int64ResultNode = expression::Int64ResultNode;
    DECLARE_AGGREGATIONRESULT(XorAggregationResult);
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const Int64ResultNode & getXor() const { return _xor; }
    XorAggregationResult &setXor(const Int64ResultNode &i) {
        _xor = i;
        return *this;
    }
private:
    const ResultNode & onGetRank() const override { return getXor(); }
    void onPrepare(const ResultNode & result, bool useForInit) override;
    Int64ResultNode _xor;
};

}
