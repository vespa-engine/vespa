// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressioncountaggregationresult.h"

namespace search::aggregation {

/** Counts distinct nonempty expression values, including individual values of a vector. */
class UniqueCountAggregationResult : public ExpressionCountAggregationResult {
public:
    DECLARE_IDENTIFIABLE_NS2(search, aggregation, UniqueCountAggregationResult);
    UniqueCountAggregationResult();
    ~UniqueCountAggregationResult() override;
    UniqueCountAggregationResult* clone() const override { return new UniqueCountAggregationResult(*this); }

private:
    mutable expression::Int64ResultNode _estimated_count;
    mutable bool _estimate_valid = false;

    const ResultNode& onGetRank() const override;
    void onMerge(const AggregationResult& result) override;
    void onReset() override;
    vespalib::Deserializer& onDeserialize(vespalib::Deserializer& stream) override;
    void onAggregate(const ResultNode& result) override;
};

} // namespace search::aggregation
