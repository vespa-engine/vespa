// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "aggregationresult.h"
#include <vespa/searchlib/expression/singleresultnode.h>
#include <vespa/vespalib/util/kll_sketch.h>

namespace search::aggregation {

using SingleResultNode = expression::SingleResultNode;

class QuantileAggregationResult : public AggregationResult
{
    double                _quantile{};
    SingleResultNode::CP  _value;
    vespalib::KLLSketch   _sketch;

public:
    QuantileAggregationResult();
    explicit QuantileAggregationResult(const ResultNode::CP& result);
    ~QuantileAggregationResult() override;
    QuantileAggregationResult(const QuantileAggregationResult&);
    QuantileAggregationResult& operator=(const QuantileAggregationResult&);

    DECLARE_AGGREGATIONRESULT(QuantileAggregationResult);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    [[nodiscard]] const SingleResultNode& value() const { return *_value; }

private:
    [[nodiscard]] const ResultNode& onGetRank() const override { return value(); }
    void onPrepare(const ResultNode& result, bool useForInit) override;
};

}
