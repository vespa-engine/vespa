// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "argmax_aggregation_result.h"

#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>

#include <vespa/searchlib/aggregation/aggregation.hpp>
#include <vespa/vespalib/objects/visit.hpp>

namespace search::aggregation {

using expression::ExpressionTree;
using expression::FloatResultNode;
using expression::ResultNode;
using expression::ResultNodeVector;
using expression::SingleResultNode;

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_AGGREGATIONRESULT(ArgmaxAggregationResult, AggregationResult);

ArgmaxAggregationResult::ArgmaxAggregationResult()
    : AggregationResult(),
      _key_tree(std::make_shared<ExpressionTree>()),
      _key(FloatResultNode(0.0)),
      _scratch_key(FloatResultNode(0.0)),
      _value(FloatResultNode(0.0)),
      _has_value(false) {
}

ArgmaxAggregationResult::ArgmaxAggregationResult(const SingleResultNode& key, const ResultNode& value)
    : AggregationResult(),
      _key_tree(std::make_shared<ExpressionTree>()),
      _key(key),
      _scratch_key(key),
      _value(value),
      _has_value(true) {
}

ArgmaxAggregationResult::~ArgmaxAggregationResult() = default;

ArgmaxAggregationResult& ArgmaxAggregationResult::set_key_expression(ExpressionNode::UP key) {
    _key_tree = std::make_shared<ExpressionTree>(std::move(key));
    // Pick up the key type right away when the value expression is already prepared.
    const ExpressionNode* value_expr = getExpression();
    if (value_expr != nullptr && value_expr->getResult() != nullptr) {
        onPrepare(*value_expr->getResult());
    }
    return *this;
}

void ArgmaxAggregationResult::onPrepare(const ResultNode& result) {
    if (!is_ready(_value.get(), result)) {
        _value.reset(static_cast<ResultNode*>(result.getClass().create()));
    }
    const ResultNode* key_result = (_key_tree->getRoot() != nullptr) ? _key_tree->getResult() : nullptr;
    if (key_result != nullptr) {
        auto wanted_key = create_and_ensure_wanted<SingleResultNode, FloatResultNode>(*key_result);
        if (!is_ready(_key.get(), *wanted_key)) {
            _key = std::move(wanted_key);
        }
    } else if (_key.get() == nullptr) {
        _key = std::make_unique<FloatResultNode>();
    }
    if (!is_ready(_scratch_key.get(), *_key)) {
        _scratch_key.reset(static_cast<SingleResultNode*>(_key->getClass().create()));
    }
}

void ArgmaxAggregationResult::initForUnitTest(const ResultNode& result) {
    onPrepare(result);
    _value.reset(result.clone());
    _has_value = true;
}

bool ArgmaxAggregationResult::fill_scratch_key() {
    const ResultNode* key = (_key_tree->getRoot() != nullptr) ? _key_tree->getResult() : nullptr;
    if (key == nullptr) {
        return false;
    }
    // Start from the smallest representable key so that NaN, which fails every comparison, is never picked.
    _scratch_key->setMin();
    if (key->isMultiValue()) {
        bool        ok = false;
        const auto& key_values = static_cast<const ResultNodeVector&>(*key);
        for (const auto& key_value : key_values) {
            if (key_value.getFloat() >= _scratch_key->getFloat()) {
                _scratch_key->set(key_value);
                ok = true;
            }
        }
        return ok;
    } else {
        if (key->getFloat() >= _scratch_key->getFloat()) {
            _scratch_key->set(*key);
            return true;
        }
    }
    return false;
}

void ArgmaxAggregationResult::update_value(const ResultNode& result) {
    if (_has_value && _scratch_key->cmp(*_key) <= 0) {
        return;
    }
    if (result.isMultiValue()) {
        _value.reset(result.clone());
    } else {
        _value->set(result);
    }
    _key->set(*_scratch_key);
    _has_value = true;
}

void ArgmaxAggregationResult::onAggregate(const ResultNode& result) {
    if (fill_scratch_key()) {
        update_value(result);
    }
}

void ArgmaxAggregationResult::onAggregate(const ResultNode& result, DocId docId, HitRank rank) {
    if (_key_tree->getRoot() != nullptr) {
        // An integer attribute encodes a missing value as its type minimum, which the key comparison cannot
        // tell apart from a real value. Ask the attribute instead, so a hit without a key is never selected.
        if (_key_tree->has_undefined_attribute(docId)) {
            return;
        }
        _key_tree->execute(docId, rank);
    }
    onAggregate(result);
}

void ArgmaxAggregationResult::onAggregate(const ResultNode& result, const document::Document& doc, HitRank rank) {
    if (_key_tree->getRoot() != nullptr) {
        _key_tree->execute(doc, rank);
    }
    onAggregate(result);
}

void ArgmaxAggregationResult::onMerge(const AggregationResult& b) {
    const auto& rhs = static_cast<const ArgmaxAggregationResult&>(b);
    if (!rhs._has_value || (_has_value && rhs._key->cmp(*_key) <= 0)) {
        return;
    }
    _value = rhs._value;
    _key = rhs._key;
    _has_value = true;
}

void ArgmaxAggregationResult::onReset() {
    _value.reset(static_cast<ResultNode*>(_value->getClass().create()));
    _key.reset(static_cast<SingleResultNode*>(_key->getClass().create()));
    _has_value = false;
}

Serializer& ArgmaxAggregationResult::onSerialize(Serializer& os) const {
    AggregationResult::onSerialize(os);
    os << *_key_tree;
    return os << _has_value << _key << _value;
}

Deserializer& ArgmaxAggregationResult::onDeserialize(Deserializer& is) {
    AggregationResult::onDeserialize(is);
    _key_tree = std::make_shared<ExpressionTree>();
    is >> *_key_tree;
    return is >> _has_value >> _key >> _value;
}

void ArgmaxAggregationResult::visitMembers(vespalib::ObjectVisitor& visitor) const {
    AggregationResult::visitMembers(visitor);
    visit(visitor, "keyExpression", _key_tree);
    visit(visitor, "hasValue", _has_value);
    visit(visitor, "key", _key);
    visit(visitor, "value", _value);
}

void ArgmaxAggregationResult::selectMembers(const vespalib::ObjectPredicate& predicate,
                                            vespalib::ObjectOperation&       operation) {
    AggregationResult::selectMembers(predicate, operation);
    _key_tree->select(predicate, operation);
}

} // namespace search::aggregation
