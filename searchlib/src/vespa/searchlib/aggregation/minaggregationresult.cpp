// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "minaggregationresult.h"

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

IMPLEMENT_AGGREGATIONRESULT(MinAggregationResult, AggregationResult);

MinAggregationResult::MinAggregationResult()
    : AggregationResult(), _min(FloatResultNode(std::numeric_limits<double>::max())) {
}

MinAggregationResult::MinAggregationResult(const SingleResultNode& min) : AggregationResult(), _min(min) {
}

MinAggregationResult::~MinAggregationResult() = default;

void MinAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_min.get(), result)) {
        _min = create_and_ensure_wanted<SingleResultNode, FloatResultNode>(result);
        _min->setMax();
    }
}

void MinAggregationResult::initForUnitTest(const ResultNode& result) {
    if (!is_ready(_min.get(), result)) {
        _min = create_and_ensure_wanted<SingleResultNode, FloatResultNode>(result);
    }
    _min->set(result);
}

void MinAggregationResult::onMerge(const AggregationResult& b) {
    _min->min(*static_cast<const MinAggregationResult&>(b)._min);
}

void MinAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector&>(result).flattenMin(*_min);
    } else {
        _min->min(result);
    }
}

void MinAggregationResult::onReset() {
    _min.reset(static_cast<SingleResultNode*>(_min->getClass().create()));
    _min->setMax();
}

Serializer& MinAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return os << _min;
}

Deserializer& MinAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return is >> _min;
}

void MinAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "min", _min);
}

} // namespace search::aggregation
