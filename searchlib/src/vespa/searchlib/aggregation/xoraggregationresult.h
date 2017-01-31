// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/integerresultnode.h>


namespace search {
namespace aggregation {

class XorAggregationResult : public AggregationResult
{
public:
    using Int64ResultNode = expression::Int64ResultNode;
    DECLARE_AGGREGATIONRESULT(XorAggregationResult);
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    const Int64ResultNode & getXor() const { return _xor; }
    XorAggregationResult &setXor(const Int64ResultNode &i) {
        _xor = i;
        return *this;
    }
private:
    virtual const ResultNode & onGetRank() const { return getXor(); }
    virtual void onPrepare(const ResultNode & result, bool useForInit);
    Int64ResultNode _xor;
};

}
}
