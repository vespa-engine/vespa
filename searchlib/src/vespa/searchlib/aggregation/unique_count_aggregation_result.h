// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressioncountaggregationresult.h"

namespace search::aggregation {

/**
 * Counts distinct expression values, including individual values of a vector.
 * Missing values are skipped: empty strings, the undefined value of integer
 * fields and NaN.
 *
 * Values are counted by their 32-bit hash. Up to sparse_limit distinct hashes
 * are kept and counted directly, so the count is not an estimate, but two
 * different values with the same hash count once. Above the limit the count
 * is a HyperLogLog estimate.
 */
class UniqueCountAggregationResult : public ExpressionCountAggregationResult {
public:
    // At this limit the estimate right after switching still uses linear
    // counting, so counts do not jump, and a sparse sketch is at most 2 KB on
    // the wire. The Java container uses the same limit.
    static constexpr uint32_t sparse_limit = 512;

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
