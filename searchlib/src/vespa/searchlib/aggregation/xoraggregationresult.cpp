// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "xoraggregationresult.h"

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

using search::expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::aggregation {

IMPLEMENT_AGGREGATIONRESULT(XorAggregationResult, AggregationResult);

void XorAggregationResult::onPrepare(const ResultNode&) {
}

void XorAggregationResult::initForUnitTest(const ResultNode& result) {
    _xor.set(result);
}

Serializer& XorAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return _xor.serialize(os);
}

Deserializer& XorAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return _xor.deserialize(is);
}

void XorAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "xor", _xor);
}

void XorAggregationResult::onMerge(const AggregationResult& b) {
    _xor.xorOp(static_cast<const XorAggregationResult&>(b)._xor);
}

void XorAggregationResult::onAggregate(const ResultNode& result) {
    if (result.isMultiValue()) {
        const auto& vec = static_cast<const ResultNodeVector&>(result);
        for (const auto& elem : vec) {
            _xor.xorOp(elem);
        }
    } else {
        _xor.xorOp(result);
    }
}

void XorAggregationResult::onReset() {
    _xor = 0;
}

} // namespace search::aggregation
