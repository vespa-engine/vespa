// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/integerresultnode.h>
#include <vespa/searchlib/grouping/hyperloglog.h>

namespace search::aggregation {

/**
 * Estimates the number of unique values of an expression that has
 * been observed. This class keeps track of the raw data needed for
 * estimation (the sketch). Actual estimation is done on the QR
 * server.
 */
class ExpressionCountAggregationResult : public AggregationResult {
    static const int PRECISION = 10;

    HyperLogLog<PRECISION>      _hll;
    expression::Int64ResultNode _rank;
    uint32_t _sparse_limit;

    [[nodiscard]] const ResultNode& onGetRank() const override { return _rank; }
    void onPrepare(const ResultNode&) override {}

public:
    DECLARE_AGGREGATIONRESULT_BASE(ExpressionCountAggregationResult);
    ExpressionCountAggregationResult* clone() const override { return new ExpressionCountAggregationResult(*this); }
    ExpressionCountAggregationResult();
    ~ExpressionCountAggregationResult() override;

    void visitMembers(vespalib::ObjectVisitor&) const override {}
    [[nodiscard]] const Sketch<PRECISION, uint32_t>& getSketch() const { return _hll.getSketch(); }

protected:
    void onMerge(const AggregationResult& result) override;
    void onAggregate(const ResultNode& result) override;
    void onReset() override;
    explicit ExpressionCountAggregationResult(uint32_t sparse_limit);
    void aggregate_value(const ResultNode& result);
};

} // namespace search::aggregation
