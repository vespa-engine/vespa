// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "and_predicate_node.h"
#include "resultnode.h"

#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, AndPredicateNode, MultiArgPredicateNode);

AndPredicateNode::AndPredicateNode() noexcept = default;

AndPredicateNode::~AndPredicateNode() = default;

AndPredicateNode::AndPredicateNode(const std::vector<FilterPredicateNode>& input)
  : MultiArgPredicateNode(input)
{
}

Serializer& AndPredicateNode::onSerialize(Serializer& os) const {
    return os << args();
}

Deserializer& AndPredicateNode::onDeserialize(Deserializer& is) {
    is >> args();
    return is;
}

bool AndPredicateNode::allow(const DocId docId, const HitRank rank) {
    for (auto& arg : args()) {
        if (!arg->allow(docId, rank)) {
            return false;
        }
    }
    return true;
}

bool AndPredicateNode::allow(const document::Document & doc, const HitRank rank) {
    for (auto& arg : args()) {
        if (!arg->allow(doc, rank)) {
            return false;
        }
    }
    return true;
}

} // namespace search::expression
