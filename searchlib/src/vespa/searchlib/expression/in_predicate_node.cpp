// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "in_predicate_node.h"

#include "resultnode.h"
#include "resultvector.h"

#include <vespa/vespalib/objects/deserializer.hpp>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/visit.hpp>

#include <algorithm>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, InPredicateNode, FilterPredicateNode);

namespace {

bool matches_any(const ResultNode& value, size_t index, const std::vector<std::string>& args) {
    HoldString      held(value, index);
    std::string_view str(held);
    return std::ranges::any_of(args, [str](const std::string& arg) { return str == arg; });
}

} // namespace

bool InPredicateNode::check(const ResultNode* result) const {
    if (result->inherits(ResultNodeVector::classId)) {
        const auto& result_vector = static_cast<const ResultNodeVector&>(*result);
        for (size_t i = 0; i < result_vector.size(); i++) {
            if (matches_any(result_vector, i, _args)) {
                return true;
            }
        }
        return false;
    }
    return matches_any(*result, 0, _args);
}

bool InPredicateNode::allow(const document::Document& doc, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(doc, rank);
        return check(_argument.getResult());
    }
    return false;
}

bool InPredicateNode::allow(DocId docId, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(docId, rank);
        return check(_argument.getResult());
    }
    return false;
}

InPredicateNode::InPredicateNode() noexcept = default;

InPredicateNode::~InPredicateNode() = default;

InPredicateNode::InPredicateNode(const InPredicateNode&) = default;

InPredicateNode& InPredicateNode::operator=(const InPredicateNode&) = default;

InPredicateNode::InPredicateNode(ExpressionNode::UP input, std::vector<std::string> args)
    : _argument(std::move(input)),
      _args(std::move(args)) {
}

Serializer& InPredicateNode::onSerialize(Serializer& os) const {
    return os << _argument << _args;
}

Deserializer& InPredicateNode::onDeserialize(Deserializer& is) {
    return is >> _argument >> _args;
}

void InPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "argument", _argument);
    visit(visitor, "args", _args);
}

void InPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) {
    _argument.select(predicate, operation);
}

} // namespace search::expression
