// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maxaggregationresult.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

#include <limits>

using search::expression::FloatResultNode;
using search::expression::NumericResultNode;
using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(MaxAggregationResult, AggregationResult);

MaxAggregationResult::MaxAggregationResult()
    : AggregationResult(), _max(FloatResultNode(-std::numeric_limits<double>::max())) {
}

MaxAggregationResult::MaxAggregationResult(const SingleResultNode& max) : AggregationResult(), _max(max) {
}

MaxAggregationResult::~MaxAggregationResult() = default;

void MaxAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_max.get(), result)) {
        _max = create_and_ensure_wanted<SingleResultNode, FloatResultNode>(result);
        _max->setMin();
    }
}

void MaxAggregationResult::initForUnitTest(const ResultNode& result) {
    if (!is_ready(_max.get(), result)) {
        _max = create_and_ensure_wanted<SingleResultNode, FloatResultNode>(result);
    }
    _max->set(result);
}

void MaxAggregationResult::onMerge(const AggregationResult& b) {
    _max->max(*static_cast<const MaxAggregationResult&>(b)._max);
}

void MaxAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector&>(result).flattenMax(*_max);
    } else {
        _max->max(result);
    }
}

void MaxAggregationResult::onReset() {
    _max.reset(static_cast<SingleResultNode*>(_max->getClass().create()));
    _max->setMin();
}

Serializer& MaxAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return os << _max;
}

Deserializer& MaxAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return is >> _max;
}

void MaxAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "max", _max);
}

} // namespace search::aggregation
