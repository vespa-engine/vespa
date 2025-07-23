// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "or_predicate_node.h"
#include "resultnode.h"

#include <algorithm>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, OrPredicateNode, MultiArgPredicateNode);

OrPredicateNode::OrPredicateNode() noexcept = default;

OrPredicateNode::~OrPredicateNode() = default;

OrPredicateNode::OrPredicateNode(const OrPredicateNode&) = default;

OrPredicateNode& OrPredicateNode::operator=(const OrPredicateNode&) = default;

bool OrPredicateNode::allow(const DocId docId, const HitRank rank) {
    return std::ranges::any_of(args(), [docId, rank](auto arg){ return arg->allow(docId, rank); });
}

bool OrPredicateNode::allow(const document::Document& doc, const HitRank rank) {
    return std::ranges::any_of(args(), [&doc, rank](auto arg){ return arg->allow(doc, rank); });
}

} // namespace search::expression
