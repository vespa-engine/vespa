// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/grouping/hyperloglog.h>
#include <vespa/searchlib/expression/integerresultnode.h>

namespace search {
namespace aggregation {

/**
 * Estimates the number of unique values of an expression that has
 * been observed. This class keeps track of the raw data needed for
 * estimation (the sketch). Actual estimation is done on the QR
 * server.
 */
class ExpressionCountAggregationResult : public AggregationResult {
    static const int PRECISION = 10;

    HyperLogLog<PRECISION> _hll;
    expression::Int64ResultNode _rank;

    const ResultNode & onGetRank() const override { return _rank; }
    void onPrepare(const ResultNode &, bool) override { }
public:
    DECLARE_AGGREGATIONRESULT(ExpressionCountAggregationResult);
    ExpressionCountAggregationResult() : AggregationResult(), _hll() { }

    void visitMembers(vespalib::ObjectVisitor &) const override {}
    const Sketch<PRECISION, uint32_t> &getSketch() const { return _hll.getSketch(); }
};

}  // namespace aggregation
}  // namespace search

