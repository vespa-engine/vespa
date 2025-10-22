// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weak_and_query_node.h"
#include "query_visitor.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::streaming {

WeakAndQueryNode::~WeakAndQueryNode() = default;

void
WeakAndQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    OrQueryNode::visitMembers(visitor);
    visit(visitor, "targetNumHits", static_cast<uint64_t>(_targetNumHits));
    visit(visitor, "view", _view);
}

void
WeakAndQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
