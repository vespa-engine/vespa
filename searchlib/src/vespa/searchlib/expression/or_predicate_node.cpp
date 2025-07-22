// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "or_predicate_node.h"
#include "resultnode.h"

#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

namespace search::expression {

using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, OrPredicateNode, MultiArgPredicateNode);

OrPredicateNode::OrPredicateNode() noexcept = default;

OrPredicateNode::~OrPredicateNode() = default;

OrPredicateNode::OrPredicateNode(const std::vector<FilterPredicateNode>& input)
  : MultiArgPredicateNode(input)
{
}

Serializer& OrPredicateNode::onSerialize(Serializer& os) const {
    return os << args();
}

Deserializer& OrPredicateNode::onDeserialize(Deserializer& is) {
    is >> args();
    return is;
}

bool OrPredicateNode::allow(const DocId docId, const HitRank rank) {
    for (auto& arg : args()) {
        if (arg->allow(docId, rank)) {
            return true;
        }
    }
    return false;
}

bool OrPredicateNode::allow(const document::Document & doc, const HitRank rank) {
    for (auto& arg : args()) {
        if (arg->allow(doc, rank)) {
            return true;
        }
    }
    return false;
}

} // namespace search::expression
