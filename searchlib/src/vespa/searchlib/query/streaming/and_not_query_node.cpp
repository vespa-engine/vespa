// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "and_not_query_node.h"
#include "query_visitor.h"

namespace search::streaming {

AndNotQueryNode::~AndNotQueryNode() = default;

bool
AndNotQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    auto it = getChildren().begin();
    auto mt = getChildren().end();
    bool result = it != mt && (*it)->evaluate();
    if (result) {
        for (++it; it != mt; it++) {
            if ((*it)->evaluate()) {
                result = false;
                break;
            }
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
AndNotQueryNode::get_element_ids(std::vector<uint32_t>&)
{
}

void
AndNotQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
