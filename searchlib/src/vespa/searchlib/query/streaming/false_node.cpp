// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "false_node.h"
#include "query_visitor.h"

namespace search::streaming {

FalseNode::~FalseNode() = default;

bool
FalseNode::evaluate()
{
    return false;
}

void
FalseNode::get_element_ids(std::vector<uint32_t>&)
{
}

void
FalseNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
