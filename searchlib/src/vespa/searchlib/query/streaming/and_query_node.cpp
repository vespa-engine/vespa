// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "and_query_node.h"
#include "query_visitor.h"
#include <algorithm>
#include <span>

namespace search::streaming {

AndQueryNode::~AndQueryNode() = default;

bool
AndQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = !getChildren().empty();
    for (const auto& qn : getChildren()) {
        if (!qn->evaluate()) {
            result = false;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

void
AndQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    auto& children = getChildren();
    if (children.empty()) {
        return;
    }
    children.front()->get_element_ids(element_ids);
    std::span others(children.begin() + 1, children.end());
    if (others.empty() || element_ids.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    for (auto& child : others) {
        temp_element_ids.clear();
        result.clear();
        child->get_element_ids(temp_element_ids);
        std::set_intersection(element_ids.begin(), element_ids.end(),
                              temp_element_ids.begin(), temp_element_ids.end(),
                              std::back_inserter(result));
        std::swap(element_ids, result);
        if (element_ids.empty()) {
            return;
        }
    }
}

void
AndQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
