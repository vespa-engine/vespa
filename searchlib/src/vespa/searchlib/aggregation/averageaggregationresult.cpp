// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "averageaggregationresult.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/integerresultnode.h>
#include <vespa/searchlib/expression/numericresultnode.h>
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

IMPLEMENT_AGGREGATIONRESULT(AverageAggregationResult, AggregationResult);

AverageAggregationResult::AverageAggregationResult() : AggregationResult(), _sum(FloatResultNode(0.0)), _count(0) {
}

AverageAggregationResult::~AverageAggregationResult() = default;

void AverageAggregationResult::onPrepare(const ResultNode&) {
    if (!_sum.get()) {
        _sum = std::make_unique<FloatResultNode>();
    }
}

void AverageAggregationResult::initForUnitTest(const ResultNode& result) {
    onPrepare(result);
    _sum->set(result);
    _count = 1;
}

void AverageAggregationResult::onMerge(const AggregationResult& b) {
    const auto& avg(static_cast<const AverageAggregationResult&>(b));
    _sum->add(*avg._sum);
    _count += avg._count;
}

void AverageAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector&>(result).flattenSum(*_sum);
        _count += static_cast<const ResultNodeVector&>(result).size();
    } else {
        _sum->add(result);
        _count++;
    }
}

void AverageAggregationResult::onReset() {
    _count = 0;
    _sum.reset(static_cast<NumericResultNode*>(_sum->getClass().create()));
}

const NumericResultNode& AverageAggregationResult::getAverage() const {
    _averageScratchPad = _sum;
    if (_count > 0) {
        _averageScratchPad->divide(Int64ResultNode(_count));
    } else {
        _averageScratchPad->set(Int64ResultNode(0));
    }
    return *_averageScratchPad;
}

Serializer& AverageAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return os.put(_count) << _sum;
}

Deserializer& AverageAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return is.get(_count) >> _sum;
}

void AverageAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
    visit(visitor, "sum", _sum);
}

} // namespace search::aggregation
