// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "quantile_aggregation_result.h"

#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

namespace search::aggregation {

using search::expression::ResultNodeVector;
using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, QuantileAggregationResult, AggregationResult);

QuantileAggregationResult::QuantileAggregationResult() {
    _value.reset(new expression::FloatResultNode());
}

QuantileAggregationResult::QuantileAggregationResult(const ResultNode::CP& result)
    : AggregationResult() {
    setResult(result);
}

QuantileAggregationResult::~QuantileAggregationResult() = default;

QuantileAggregationResult::QuantileAggregationResult(const QuantileAggregationResult&) = default;

QuantileAggregationResult& QuantileAggregationResult::operator=(const QuantileAggregationResult&) = default;

void QuantileAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "quantile", _quantile);
    visit(visitor, "value", value());
}

void QuantileAggregationResult::onPrepare(const ResultNode& result, bool useForInit) {
    (void) result;
    (void) useForInit;
}

void QuantileAggregationResult::onMerge(const AggregationResult& b) {
    _sketch.merge(dynamic_cast<const QuantileAggregationResult&>(b)._sketch);
}

void QuantileAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        auto& rv = static_cast<const ResultNodeVector &>(result);
        for (size_t i = 0; i < rv.size(); ++i) {
            _sketch.update(rv.get(i).getFloat());
        }
    } else {
        _sketch.update(result.getFloat());
    }
}

void QuantileAggregationResult::onReset() {
    _sketch = vespalib::KLLSketch();
}

Serializer& QuantileAggregationResult::onSerialize(vespalib::Serializer& os) const {
    AggregationResult::onSerialize(os);
    os << _quantile;

    std::vector<uint8_t> buf = _sketch.serialize();
    os << buf;

    return os;
}

Deserializer& QuantileAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    is >> _quantile;

    std::vector<uint8_t> buf;
    is >> buf;
    _sketch = vespalib::KLLSketch::deserialize(buf);

    return is;
}


const expression::NumericResultNode& QuantileAggregationResult::value() const noexcept {
    if (_sketch.is_empty()) {
        _value->set(FloatResultNode(0));
    } else {
        _value->set(FloatResultNode(_sketch.get_quantile(_quantile)));
    }
    return *_value;
}

void QuantileAggregationResult::set_quantile(double quantile) {
    _quantile = quantile;
}

double QuantileAggregationResult::quantile() const {
    return _quantile;
}

void QuantileAggregationResult::update_sketch(double value) {
    _sketch.update(value);
}
} // namespace search::aggregation
