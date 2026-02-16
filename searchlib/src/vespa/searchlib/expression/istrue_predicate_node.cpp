// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "istrue_predicate_node.h"
#include "integerresultnode.h"

#include <vespa/vespalib/util/exceptions.h>

#include <format>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, IsTruePredicateNode, FilterPredicateNode);

namespace {

const BoolResultNode* as_bool_result(const ResultNode* result) {
    return static_cast<const BoolResultNode*>(result);
}

}

bool IsTruePredicateNode::check(const ResultNode* result) const {
    if (!result->inherits(BoolResultNode::classId)) {
        throw vespalib::IllegalArgumentException(
            std::format("istrue() requires boolean input, got {}", result->getClass().name()));
    }

    return as_bool_result(result)->getBool();
}

bool IsTruePredicateNode::allow(const document::Document& doc, HitRank rank) {
    if (_expression.getRoot()) {
        _expression.execute(doc, rank);
        return check(_expression.getResult());
    }
    return false;
}

bool IsTruePredicateNode::allow(DocId docId, HitRank rank) {
    if (_expression.getRoot()) {
        _expression.execute(docId, rank);
        return check(_expression.getResult());
    }
    return false;
}

IsTruePredicateNode::IsTruePredicateNode() noexcept = default;

IsTruePredicateNode::~IsTruePredicateNode() = default;

IsTruePredicateNode::IsTruePredicateNode(const IsTruePredicateNode&) = default;

IsTruePredicateNode& IsTruePredicateNode::operator=(const IsTruePredicateNode&) = default;

IsTruePredicateNode::IsTruePredicateNode(ExpressionNode::UP input)
  : _expression(std::move(input))
{
}

Serializer& IsTruePredicateNode::onSerialize(Serializer& os) const {
    return os << _expression;
}

Deserializer& IsTruePredicateNode::onDeserialize(Deserializer& is) {
    is >> _expression;
    return is;
}

void IsTruePredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "expression", _expression);
}

void IsTruePredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                        vespalib::ObjectOperation& operation) {
    _expression.select(predicate, operation);
}

} // namespace search::expression
