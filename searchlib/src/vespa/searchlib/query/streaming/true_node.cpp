// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "true_node.h"
#include "query_visitor.h"

namespace search::streaming {

TrueNode::~TrueNode() = default;

bool
TrueNode::evaluate()
{
    return true;
}

void
TrueNode::get_element_ids(std::vector<uint32_t>&)
{
}

void
TrueNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
