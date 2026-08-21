// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_aggregation_result.h"

#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/vespalib/objects/visit.hpp>

namespace search::aggregation {

using expression::ExpressionTree;
using expression::FloatResultNode;
using expression::ResultNode;
using expression::ResultNodeVector;
using expression::SingleResultNode;

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, FirstAggregationResult, AggregationResult);

namespace {

[[nodiscard]] bool is_ready(const ResultNode* current, const ResultNode& wanted) noexcept {
    return current != nullptr && current->getClass().id() == wanted.getClass().id();
}

[[nodiscard]] std::unique_ptr<SingleResultNode> create_result_node(const ResultNode& result) {
    auto base = result.createBaseType();
    if (dynamic_cast<SingleResultNode*>(base.get()) != nullptr) {
        return std::unique_ptr<SingleResultNode>(static_cast<SingleResultNode*>(base.release()));
    }
    return std::make_unique<FloatResultNode>();
}

} // namespace

FirstAggregationResult::FirstAggregationResult()
    : AggregationResult(),
      _key_tree(std::make_shared<ExpressionTree>()),
      _key(FloatResultNode(0.0)),
      _scratch_key(FloatResultNode(0.0)),
      _first(FloatResultNode(0.0)),
      _has_value(false) {
}

FirstAggregationResult::FirstAggregationResult(const SingleResultNode& first, const SingleResultNode& key)
    : AggregationResult(),
      _key_tree(std::make_shared<ExpressionTree>()),
      _key(key),
      _scratch_key(key),
      _first(first),
      _has_value(true) {
}

FirstAggregationResult::~FirstAggregationResult() = default;

FirstAggregationResult& FirstAggregationResult::set_key_expression(ExpressionNode::UP key) {
    _key_tree = std::make_shared<ExpressionTree>(std::move(key));
    return *this;
}

void FirstAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_first.get(), result)) {
        _first = create_result_node(result);
    }
    const ResultNode* key_result = (_key_tree->getRoot() != nullptr) ? _key_tree->getResult() : nullptr;
    if (key_result != nullptr && !is_ready(_key.get(), *key_result)) {
        _key = create_result_node(*key_result);
    } else if (_key.get() == nullptr) {
        _key = std::make_unique<FloatResultNode>();
    }
    if (!is_ready(_scratch_key.get(), *_key)) {
        _scratch_key.reset(static_cast<SingleResultNode*>(_key->getClass().create()));
    }
}

void FirstAggregationResult::initForUnitTest(const ResultNode& result) {
    onPrepare(result);
    _first->set(result);
    _has_value = true;
}

bool FirstAggregationResult::fill_scratch_key(HitRank rank) {
    if (_key_tree->getRoot() == nullptr) {
        _scratch_key->set(FloatResultNode(-rank));
        return true;
    }
    const ResultNode* key = _key_tree->getResult();
    if (key == nullptr) {
        return false;
    }
    if (key->isMultiValue()) {
        const auto& values = static_cast<const ResultNodeVector&>(*key);
        if (values.empty()) {
            return false;
        }
        _scratch_key->set(values.get(0));
    } else {
        _scratch_key->set(*key);
    }
    return true;
}

void FirstAggregationResult::update_first(const ResultNode& result) {
    if (_has_value && _scratch_key->cmp(*_key) >= 0) {
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
    _key->set(*_scratch_key);
    _has_value = true;
}

void FirstAggregationResult::onAggregate(const ResultNode& result) {
    if (fill_scratch_key(zero_rank_value)) {
        update_first(result);
    }
}

void FirstAggregationResult::onAggregate(const ResultNode& result, DocId docId, HitRank rank) {
    if (_key_tree->getRoot() != nullptr) {
        _key_tree->execute(docId, rank);
    }
    if (fill_scratch_key(rank)) {
        update_first(result);
    }
}

void FirstAggregationResult::onAggregate(const ResultNode& result, const document::Document& doc, HitRank rank) {
    if (_key_tree->getRoot() != nullptr) {
        _key_tree->execute(doc, rank);
    }
    if (fill_scratch_key(rank)) {
        update_first(result);
    }
}

void FirstAggregationResult::onMerge(const AggregationResult& b) {
    const auto& rhs = static_cast<const FirstAggregationResult&>(b);
    if (!rhs._has_value || (_has_value && rhs._key->cmp(*_key) >= 0)) {
        return;
    }
    _first = rhs._first;
    _key = rhs._key;
    _has_value = true;
}

void FirstAggregationResult::onReset() {
    _first.reset(static_cast<SingleResultNode*>(_first->getClass().create()));
    _key.reset(static_cast<SingleResultNode*>(_key->getClass().create()));
    _has_value = false;
}

Serializer& FirstAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    os << *_key_tree;
    return os << _has_value << _key << _first;
}

Deserializer& FirstAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    _key_tree = std::make_shared<ExpressionTree>();
    is >> *_key_tree;
    return is >> _has_value >> _key >> _first;
}

void FirstAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "keyExpression", _key_tree);
    visit(visitor, "hasValue", _has_value);
    visit(visitor, "key", _key);
    visit(visitor, "first", _first);
}

void FirstAggregationResult::selectMembers(const vespalib::ObjectPredicate& predicate,
                                           vespalib::ObjectOperation&       operation) {
    AggregationResult::selectMembers(predicate, operation);
    _key_tree->select(predicate, operation);
}

} // namespace search::aggregation
