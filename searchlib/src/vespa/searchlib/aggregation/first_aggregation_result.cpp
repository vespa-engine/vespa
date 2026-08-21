// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_aggregation_result.h"

#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/vespalib/objects/visit.hpp>

namespace search::aggregation {

using expression::FloatResultNode;
using expression::ResultNodeVector;
using expression::SingleResultNode;

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, FirstAggregationResult, AggregationResult);

namespace {

[[nodiscard]] bool is_ready(const expression::ResultNode* current, const expression::ResultNode& wanted) noexcept {
    return current != nullptr && current->getClass().id() == wanted.getClass().id();
}

[[nodiscard]] std::unique_ptr<SingleResultNode> create_result_node(const expression::ResultNode& result) {
    auto base = result.createBaseType();
    if (dynamic_cast<SingleResultNode*>(base.get()) != nullptr) {
        return std::unique_ptr<SingleResultNode>(static_cast<SingleResultNode*>(base.release()));
    }
    return std::make_unique<FloatResultNode>();
}

} // namespace

FirstAggregationResult::FirstAggregationResult()
    : AggregationResult(), _first(FloatResultNode(0.0)), _hit_rank(default_rank_value), _has_value(false) {
}

FirstAggregationResult::FirstAggregationResult(const SingleResultNode& first, HitRank hit_rank)
    : AggregationResult(), _first(first), _hit_rank(hit_rank), _has_value(true) {
}

FirstAggregationResult::~FirstAggregationResult() = default;

void FirstAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_first.get(), result)) {
        _first = create_result_node(result);
    }
}

void FirstAggregationResult::initForUnitTest(const ResultNode& result) {
    onPrepare(result);
    _first->set(result);
    _has_value = true;
}

void FirstAggregationResult::update_first(const ResultNode& result, HitRank rank) {
    if (_has_value && rank <= _hit_rank) {
        return;
    }
    if (result.isMultiValue()) {
        const auto& values = static_cast<const ResultNodeVector&>(result);
        if (values.empty()) {
            return;
        }
        _first->set(values.get(0));
    } else {
        _first->set(result);
    }
    _hit_rank = rank;
    _has_value = true;
}

void FirstAggregationResult::onAggregate(const ResultNode& result) {
    update_first(result, zero_rank_value);
}

void FirstAggregationResult::onAggregate(const ResultNode& result, DocId, HitRank rank) {
    update_first(result, rank);
}

void FirstAggregationResult::onAggregate(const ResultNode& result, const document::Document&, HitRank rank) {
    update_first(result, rank);
}

void FirstAggregationResult::onMerge(const AggregationResult& b) {
    const auto& rhs = static_cast<const FirstAggregationResult&>(b);
    if (!rhs._has_value || (_has_value && rhs._hit_rank <= _hit_rank)) {
        return;
    }
    _first->set(*rhs._first);
    _hit_rank = rhs._hit_rank;
    _has_value = true;
}

void FirstAggregationResult::onReset() {
    _first.reset(static_cast<SingleResultNode*>(_first->getClass().create()));
    _hit_rank = default_rank_value;
    _has_value = false;
}

Serializer& FirstAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    return os << _has_value << _hit_rank << _first;
}

Deserializer& FirstAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    return is >> _has_value >> _hit_rank >> _first;
}

void FirstAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "hasValue", _has_value);
    visit(visitor, "hitRank", _hit_rank);
    visit(visitor, "first", _first);
}

} // namespace search::aggregation
