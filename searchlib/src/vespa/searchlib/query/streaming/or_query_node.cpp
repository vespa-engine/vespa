// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "or_query_node.h"
#include "query_visitor.h"
#include <algorithm>

namespace search::streaming {

OrQueryNode::~OrQueryNode() = default;

bool
OrQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = false;
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) {
            result = true;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
OrQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    auto& children = getChildren();
    if (children.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    for (auto& child : children) {
        temp_element_ids.clear();
        child->get_element_ids(temp_element_ids);
        if (!temp_element_ids.empty()) {
            result.clear();
            std::set_union(element_ids.begin(), element_ids.end(),
                           temp_element_ids.begin(), temp_element_ids.end(),
                           std::back_inserter(result));
            std::swap(element_ids, result);
        }
    }
}

void
OrQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
