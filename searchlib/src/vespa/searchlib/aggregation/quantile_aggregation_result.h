// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>
#include <vespa/vespalib/util/kll_sketch.h>

#include "vespa/searchlib/expression/floatresultnode.h"

namespace search::aggregation {

using SingleResultNode = expression::SingleResultNode;
using FloatResultNode = expression::FloatResultNode;

/*
 * Aggregator that calculates quantiles.
 */
class QuantileAggregationResult : public AggregationResult
{
    double                       _quantile{};
    vespalib::KLLSketch          _sketch;
    mutable FloatResultNode::CP  _value; // copy for getRank

public:
    QuantileAggregationResult();
    explicit QuantileAggregationResult(const ResultNode::CP& result);
    ~QuantileAggregationResult() override;
    QuantileAggregationResult(const QuantileAggregationResult&);
    QuantileAggregationResult& operator=(const QuantileAggregationResult&);

    DECLARE_AGGREGATIONRESULT(QuantileAggregationResult);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;

    const expression::NumericResultNode& value() const noexcept;

    void set_quantile(double quantile);

    double quantile() const;

    // for testing
    void update_sketch(double value);

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return value(); }
    void onPrepare(const ResultNode& result, bool useForInit) override;
};

}
