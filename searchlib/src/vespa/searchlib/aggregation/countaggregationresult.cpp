// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggregation.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(CountAggregationResult, AggregationResult);

CountAggregationResult::CountAggregationResult(uint64_t count) : AggregationResult(), _count(count) {
}

void CountAggregationResult::initForUnitTest(const ResultNode& result) {
    _count.set(result);
}

void CountAggregationResult::onPrepare(const ResultNode&) {
}

void CountAggregationResult::onReset() {
    setCount(0);
}

void SumAggregationResult::onReset() {
    _sum.reset(static_cast<NumericResultNode*>(_sum->getClass().create()));
}

void CountAggregationResult::onMerge(const AggregationResult& b) {
    _count.add(static_cast<const CountAggregationResult&>(b)._count);
}

void CountAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        _count += static_cast<const ResultNodeVector&>(result).size();
    } else {
        ++_count;
    }
}

Serializer& CountAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return _count.serialize(os);
}

Deserializer& CountAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return _count.deserialize(is);
}

void CountAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
}

} // namespace search::aggregation
