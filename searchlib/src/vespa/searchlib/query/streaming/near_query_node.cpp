// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_query_node.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::streaming {

bool
NearQueryNode::evaluate() const
{
    return AndQueryNode::evaluate();
}

void
NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
}

}
