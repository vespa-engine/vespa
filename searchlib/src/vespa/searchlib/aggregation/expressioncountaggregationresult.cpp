// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "expressioncountaggregationresult.h"

#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>

#include <xxhash.h>

using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(ExpressionCountAggregationResult, AggregationResult);

namespace {
// Calculates the sum of all buckets.
template <int BucketBits, typename HashT> int calculateRank(const Sketch<BucketBits, HashT>& sketch) {
    if (sketch.getClassId() == SparseSketch<BucketBits, HashT>::classId) {
        return static_cast<const SparseSketch<BucketBits, HashT>&>(sketch).getSize();
    }
    auto normal = static_cast<const NormalSketch<BucketBits, HashT>&>(sketch);
    int  rank = 0;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        rank += normal.bucket[i];
    }
    return rank;
}
} // namespace

ExpressionCountAggregationResult::ExpressionCountAggregationResult() = default;

ExpressionCountAggregationResult::~ExpressionCountAggregationResult() = default;

void ExpressionCountAggregationResult::onMerge(const AggregationResult& r) {
    const auto& result = Identifiable::cast<const ExpressionCountAggregationResult&>(r);
    _hll.merge(result._hll);
    _rank.set(calculateRank(_hll.getSketch()));
}

void ExpressionCountAggregationResult::onAggregate(const ResultNode& result) {
    size_t             hash = result.hash();
    const unsigned int seed = 42;
    hash = XXH32(&hash, sizeof(hash), seed);
    // The rank is a maintained sum of all buckets. This should give
    // almost the same ordering as the actual estimates.
    _rank += _hll.aggregate(hash);
}

void ExpressionCountAggregationResult::onReset() {
    _hll = HyperLogLog<PRECISION>();
    _rank.set(0);
}

Serializer& ExpressionCountAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    _hll.serialize(os);
    return os;
}

Deserializer& ExpressionCountAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    _hll.deserialize(is);
    _rank.set(calculateRank(_hll.getSketch()));
    return is;
}

} // namespace search::aggregation
