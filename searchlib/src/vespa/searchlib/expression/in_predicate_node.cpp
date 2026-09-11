// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "in_predicate_node.h"

#include "resultnode.h"
#include "resultvector.h"

#include <vespa/vespalib/objects/deserializer.hpp>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/util/exceptions.h>

#include <algorithm>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, InPredicateNode, FilterPredicateNode);

namespace {

bool equal_string(const ResultNode& value, size_t index, const ResultNode& arg) {
    HoldString lhs(value, index);
    HoldString rhs(arg);
    return std::string_view(lhs) == std::string_view(rhs);
}

} // namespace

bool InPredicateNode::check(const ResultNode* result) const {
    for (const auto& arg : _args) {
        const ResultNode* arg_result = arg.getResult();
        if (result->inherits(ResultNodeVector::classId)) {
            const auto& result_vector = static_cast<const ResultNodeVector&>(*result);
            for (size_t i = 0; i < result_vector.size(); i++) {
                if (equal_string(result_vector, i, *arg_result)) {
                    return true;
                }
            }
        } else if (equal_string(*result, 0, *arg_result)) {
            return true;
        }
    }
    return false;
}

bool InPredicateNode::allow(const document::Document& doc, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(doc, rank);
        for (const auto& arg : _args) {
            arg.execute(doc, rank);
        }
        return check(_argument.getResult());
    }
    return false;
}

bool InPredicateNode::allow(DocId docId, HitRank rank) {
    if (_argument.getRoot()) {
        _argument.execute(docId, rank);
        for (const auto& arg : _args) {
            arg.execute(docId, rank);
        }
        return check(_argument.getResult());
    }
    return false;
}

InPredicateNode::InPredicateNode() noexcept = default;

InPredicateNode::~InPredicateNode() = default;

InPredicateNode::InPredicateNode(const InPredicateNode&) = default;

InPredicateNode& InPredicateNode::operator=(const InPredicateNode&) = default;

InPredicateNode::InPredicateNode(ExpressionNode::UP input, std::vector<ExpressionNode::UP> args)
    : _argument(std::move(input)),
      _args() {
    for (auto& arg : args) {
        _args.emplace_back(std::move(arg));
    }
}

Serializer& InPredicateNode::onSerialize(Serializer& os) const {
    return os << _argument << _args;
}

Deserializer& InPredicateNode::onDeserialize(Deserializer& is) {
    is >> _argument;
    is >> _args;

    if (std::ranges::any_of(_args, [](const auto& arg) { return arg.getRoot() == nullptr; })) {
        throw vespalib::IllegalArgumentException("In predicate node received non-present argument node.");
    }

    return is;
}

void InPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "argument", _argument);
    visit(visitor, "args", _args);
}

void InPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) {
    _argument.select(predicate, operation);
    for (auto& arg : _args) {
        arg.select(predicate, operation);
    }
}

} // namespace search::expression
