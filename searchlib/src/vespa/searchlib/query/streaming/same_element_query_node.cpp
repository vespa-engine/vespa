// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_query_node.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <algorithm>
#include <span>

namespace search::streaming {

SameElementQueryNode::SameElementQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms) noexcept
    : MultiTerm(std::move(result_base), index, num_terms)
{
}

SameElementQueryNode::~SameElementQueryNode() = default;

bool
SameElementQueryNode::evaluate() const {
    std::vector<uint32_t> element_ids;
    get_element_ids(element_ids);
    return !element_ids.empty();
}

const HitList &
SameElementQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    return hl;
}

void
SameElementQueryNode::get_element_ids(std::vector<uint32_t>& element_ids) const
{
    const auto & children = get_terms();
    if (children.empty()) {
        return;
    }
    for (auto& child : children) {
        if (!child->evaluate()) {
            return;
        }
    }
    children.front()->get_element_ids(element_ids);
    std::span<const std::unique_ptr<QueryTerm>> others(children.begin() + 1, children.end());
    if (others.empty()) {
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
SameElementQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment&)
{
    if (evaluate()) {
        auto num_fields = td.numFields();
        /*
         * Currently reports hit for all fields for query node instead of
         * just the fields where the related subfields had matches.
         */
        for (size_t field_idx = 0; field_idx < num_fields; ++field_idx) {
            auto& tfd = td.field(field_idx);
            auto field_id = tfd.getFieldId();
            auto tmd = match_data.resolveTermField(tfd.getHandle());
            tmd->setFieldId(field_id);
            tmd->reset(docid);
        }
    }
}

bool
SameElementQueryNode::multi_index_terms() const noexcept
{
    return true;
}

bool
SameElementQueryNode::is_same_element_query_node() const noexcept
{
    return true;
}

SameElementQueryNode*
SameElementQueryNode::as_same_element_query_node() noexcept
{
    return this;
}

const SameElementQueryNode*
SameElementQueryNode::as_same_element_query_node() const noexcept
{
    return this;
}

void
SameElementQueryNode::get_hidden_leaves(QueryTermList & tl)
{
    auto& terms = get_terms();
    for (auto& term : terms) {
        term->getLeaves(tl);
    }
}

void
SameElementQueryNode::get_hidden_leaves(ConstQueryTermList & tl) const
{
    auto& terms = get_terms();
    for (auto& term : terms) {
        term->getLeaves(tl);
    }
}

}
