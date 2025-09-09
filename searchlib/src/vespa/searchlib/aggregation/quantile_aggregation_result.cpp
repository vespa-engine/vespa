// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "quantile_aggregation_result.h"

#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.aggregation.quantile_aggregation_result");

namespace search::aggregation {

using expression::ResultNodeVector;
using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, QuantileAggregationResult, AggregationResult);

QuantileAggregationResult::QuantileAggregationResult() {
    _rank.reset(new FloatResultNode(0));
}

QuantileAggregationResult::~QuantileAggregationResult() = default;

QuantileAggregationResult::QuantileAggregationResult(const QuantileAggregationResult&) = default;

QuantileAggregationResult& QuantileAggregationResult::operator=(const QuantileAggregationResult&) = default;

void QuantileAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "quantiles", _quantiles);

    for (const auto [quantile, value] : quantile_results()) {
        visit(visitor, std::format("quantile({})", quantile), value);
    }

    visit(visitor, "extension", _extension);
}

void QuantileAggregationResult::onPrepare(const ResultNode& result, bool useForInit) {
    (void) result;
    if (useForInit) {
        LOG(warning, "useForInit was true. Should not happen for QuantileAggregationResult.");
    }
}

void QuantileAggregationResult::onMerge(const AggregationResult& b) {
    _sketch.merge(dynamic_cast<const QuantileAggregationResult&>(b)._sketch);
}

void QuantileAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        auto& rv = dynamic_cast<const ResultNodeVector &>(result);
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
    os << _quantiles;

    os << _extension;

    const std::vector<uint8_t> buf = _sketch.serialize();
    os << buf;

    return os;
}

Deserializer& QuantileAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    is >> _quantiles;

    is >> _extension;

    std::vector<uint8_t> buf;
    is >> buf;
    _sketch = vespalib::KLLSketch::deserialize(buf);

    return is;
}

std::vector<QuantileAggregationResult::QuantileResult> QuantileAggregationResult::quantile_results() const {
    std::vector<QuantileResult> results;
    results.reserve(_quantiles.size());

    for (const auto quantile : _quantiles) {
        double value = 0.0;
        if (!_sketch.is_empty()) {
            value = _sketch.get_quantile(quantile);
        }
        results.emplace_back(quantile,  value);
    }

    return results;
}

void QuantileAggregationResult::set_quantiles(const std::vector<double>& quantiles) {
    _quantiles = quantiles;
}

const std::vector<double>& QuantileAggregationResult::quantiles() const {
    return _quantiles;
}

void QuantileAggregationResult::update_sketch(double value) {
    _sketch.update(value);
}
} // namespace search::aggregation
