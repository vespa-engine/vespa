// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "multi_arg_predicate_node.h"
#include "resultvector.h"

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, MultiArgPredicateNode, FilterPredicateNode);

MultiArgPredicateNode::MultiArgPredicateNode() noexcept = default;

MultiArgPredicateNode::~MultiArgPredicateNode() = default;

MultiArgPredicateNode::MultiArgPredicateNode(const std::vector<FilterPredicateNode::IP>& input) {
    for (const auto& node : input) {
        _args.emplace_back(node->clone());
    }
}

void MultiArgPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "args", _args);
}

void MultiArgPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                       vespalib::ObjectOperation& operation) {
    FilterPredicateNode::selectMembers(predicate, operation);
    for(auto& _arg : _args) {
        _arg->select(predicate, operation);
    }
}

} // namespace search::expression
