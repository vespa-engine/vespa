// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "quantile_aggregation_result.h"

namespace search::aggregation {

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, QuantileAggregationResult, AggregationResult);

QuantileAggregationResult::QuantileAggregationResult() = default;

QuantileAggregationResult::QuantileAggregationResult(const ResultNode::CP& result)
    : AggregationResult()
{
    setResult(result);
}


QuantileAggregationResult::~QuantileAggregationResult() = default;

QuantileAggregationResult::QuantileAggregationResult(const QuantileAggregationResult&) = default;

QuantileAggregationResult& QuantileAggregationResult::operator=(const QuantileAggregationResult&) = default;

void QuantileAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "quantile", _quantile);
    visit(visitor, "value", *_value);
}

void QuantileAggregationResult::onPrepare(const ResultNode& result, bool useForInit) {
    (void) result;
    (void) useForInit;
}

void QuantileAggregationResult::onMerge(const AggregationResult& b) {
    _sketch.merge(dynamic_cast<const QuantileAggregationResult&>(b)._sketch);
}

void QuantileAggregationResult::onAggregate(const ResultNode& result) {
    (void) result;
}

void QuantileAggregationResult::onReset() {
}

vespalib::Serializer& QuantileAggregationResult::onSerialize(vespalib::Serializer& os) const {
    AggregationResult::onSerialize(os);
    os << _quantile;
    os << _value;
    return os;
}

vespalib::Deserializer& QuantileAggregationResult::onDeserialize(vespalib::Deserializer& is) {
    AggregationResult::onDeserialize(is);
    is >> _quantile;
    is >> _value;
    return is;
}

} // namespace search::aggregation
