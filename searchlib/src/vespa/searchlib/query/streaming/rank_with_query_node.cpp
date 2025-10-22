// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rank_with_query_node.h"
#include "query_visitor.h"

namespace search::streaming {

RankWithQueryNode::~RankWithQueryNode() = default;

bool
RankWithQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = !getChildren().empty() && getChildren().front()->evaluate();
    _cached_evaluate_result.emplace(result);
    return result;
}

void
RankWithQueryNode::get_element_ids(std::vector<uint32_t>&)
{
}

void
RankWithQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
