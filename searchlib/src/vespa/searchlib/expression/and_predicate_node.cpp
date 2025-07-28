// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "and_predicate_node.h"
#include "resultnode.h"

#include <algorithm>

namespace search::expression {
using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, AndPredicateNode, MultiArgPredicateNode);

AndPredicateNode::AndPredicateNode() noexcept = default;

AndPredicateNode::~AndPredicateNode() = default;

AndPredicateNode::AndPredicateNode(const AndPredicateNode&) = default;

AndPredicateNode& AndPredicateNode::operator=(const AndPredicateNode&) = default;

bool AndPredicateNode::allow(const DocId docId, const HitRank rank) {
    return std::ranges::all_of(args(), [docId, rank](auto& arg){ return arg->allow(docId, rank); });
}

bool AndPredicateNode::allow(const document::Document& doc, const HitRank rank) {
    return std::ranges::all_of(args(), [&doc, rank](auto& arg){ return arg->allow(doc, rank); });
}
} // namespace search::expression
