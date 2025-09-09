// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"

#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/vespalib/util/kll_sketch.h>

namespace search::aggregation {

using FloatResultNode = expression::FloatResultNode;

/*
 * Aggregator that calculates quantiles.
 */
class QuantileAggregationResult : public AggregationResult
{
    std::vector<double>          _quantiles{};
    uint8_t                      _extension{}; // Leave a byte to make it easier to change the sketch in the future.
    vespalib::KLLSketch          _sketch;
    FloatResultNode::CP          _no_rank; // for onGetRank()

public:
    QuantileAggregationResult();
    explicit QuantileAggregationResult(const ResultNode::CP& result);
    ~QuantileAggregationResult() override;
    QuantileAggregationResult(const QuantileAggregationResult&);
    QuantileAggregationResult& operator=(const QuantileAggregationResult&);

    DECLARE_AGGREGATIONRESULT(QuantileAggregationResult);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;

    struct QuantileResult {
        double quantile;
        double value;
    };

    [[nodiscard]] std::vector<QuantileResult> quantile_results() const;

    void set_quantiles(const std::vector<double>& quantiles);

    [[nodiscard]] const std::vector<double>& quantiles() const;

    // for testing
    void update_sketch(double value);

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return *_no_rank; }
    void onPrepare(const ResultNode& result, bool useForInit) override;
};

}
