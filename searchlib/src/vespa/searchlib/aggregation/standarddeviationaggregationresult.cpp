// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "standarddeviationaggregationresult.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

using search::expression::FloatResultNode;
using search::expression::Int64ResultNode;
using search::expression::NumericResultNode;
using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(StandardDeviationAggregationResult, AggregationResult);

StandardDeviationAggregationResult::StandardDeviationAggregationResult()
    : AggregationResult(), _count(), _sum(), _sumOfSquared(), _stdDevScratchPad() {
    _stdDevScratchPad.reset(new expression::FloatResultNode());
}

StandardDeviationAggregationResult::~StandardDeviationAggregationResult() = default;

const NumericResultNode& StandardDeviationAggregationResult::getStandardDeviation() const noexcept {
    if (_count == 0) {
        _stdDevScratchPad->set(Int64ResultNode(0));
    } else {
        double variance = (_sumOfSquared.getFloat() - _sum.getFloat() * _sum.getFloat() / _count) / _count;
        double stddev = std::sqrt(variance);
        _stdDevScratchPad->set(FloatResultNode(stddev));
    }
    return *_stdDevScratchPad;
}

void StandardDeviationAggregationResult::onMerge(const AggregationResult& r) {
    const auto& result = Identifiable::cast<const StandardDeviationAggregationResult&>(r);
    _count += result._count;
    _sum.add(result._sum);
    _sumOfSquared.add(result._sumOfSquared);
}

void StandardDeviationAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector&>(result).flattenSum(_sum);
        static_cast<const ResultNodeVector&>(result).flattenSumOfSquared(_sumOfSquared);
        _count += static_cast<const ResultNodeVector&>(result).size();
    } else {
        _sum.add(result);
        FloatResultNode squared(result.getFloat());
        squared.multiply(result);
        _sumOfSquared.add(squared);
        _count++;
    }
}

void StandardDeviationAggregationResult::onReset() {
    _count = 0;
    _sum.set(0.0);
    _sumOfSquared.set(0.0);
}

Serializer& StandardDeviationAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    double sum = _sum.getFloat();
    double sumOfSquared = _sumOfSquared.getFloat();
    return os << _count << sum << sumOfSquared;
}

Deserializer& StandardDeviationAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    double sum;
    double sumOfSquared;
    auto&  r = is >> _count >> sum >> sumOfSquared;
    _sum.set(sum);
    _sumOfSquared.set(sumOfSquared);
    return r;
}

void StandardDeviationAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
    visit(visitor, "sum", _sum);
    visit(visitor, "sumOfSquared", _sumOfSquared);
}

} // namespace search::aggregation
