// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "not_predicate_node.h"
#include "resultnode.h"
#include "resultvector.h"

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, NotPredicateNode, FilterPredicateNode);

NotPredicateNode::NotPredicateNode() noexcept = default;

NotPredicateNode::~NotPredicateNode() = default;

NotPredicateNode::NotPredicateNode(const std::unique_ptr<FilterPredicateNode>& input)
  : _expression(input->clone())
{
}

Serializer& NotPredicateNode::onSerialize(Serializer& os) const {
    return os << _expression;
}

Deserializer& NotPredicateNode::onDeserialize(Deserializer& is) {
    is >> _expression;
    return is;
}

bool NotPredicateNode::allow(DocId docId, HitRank rank) {
    return !_expression->allow(docId, rank);
}

bool NotPredicateNode::allow(const document::Document & doc, HitRank rank) {
    return !_expression->allow(doc, rank);
}

void NotPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "expression", _expression);
}

void NotPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                       vespalib::ObjectOperation& operation) {
    _expression->select(predicate, operation);
}

} // namespace search::expression
