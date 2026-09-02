// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sumaggregationresult.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

using search::expression::FloatResultNode;
using search::expression::NumericResultNode;
using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(SumAggregationResult, AggregationResult);

SumAggregationResult::SumAggregationResult() : AggregationResult(), _sum(FloatResultNode(0.0)) {
}

SumAggregationResult::SumAggregationResult(NumericResultNode::UP sum) : AggregationResult(), _sum(sum.release()) {
}

SumAggregationResult::~SumAggregationResult() = default;

void SumAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_sum.get(), result)) {
        _sum = create_and_ensure_wanted<NumericResultNode, FloatResultNode>(result);
    }
}

void SumAggregationResult::initForUnitTest(const ResultNode& result) {
    onPrepare(result);
    _sum->set(result);
}

void SumAggregationResult::onMerge(const AggregationResult& b) {
    _sum->add(*static_cast<const SumAggregationResult&>(b)._sum);
}

void SumAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector&>(result).flattenSum(*_sum);
    } else {
        _sum->add(result);
    }
}

void SumAggregationResult::onReset() {
    _sum.reset(static_cast<NumericResultNode*>(_sum->getClass().create()));
}

Serializer& SumAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return os << _sum;
}

Deserializer& SumAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return is >> _sum;
}

void SumAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "sum", _sum);
}

} // namespace search::aggregation
