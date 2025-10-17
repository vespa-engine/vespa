// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_query_node.h"
#include "query_term_data.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <algorithm>
#include <span>

using search::common::ElementIds;
using search::fef::IIndexEnvironment;
using search::fef::ITermData;
using search::fef::MatchData;

namespace search::streaming {

SameElementQueryNode::SameElementQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, string index, uint32_t num_terms) noexcept
    : QueryTerm(std::move(result_base), "", index, Type::WORD, Normalizing::NONE),
      _children(),
      _element_ids(),
      _cached_evaluate_result()
{
    _children.reserve(num_terms);
}

SameElementQueryNode::~SameElementQueryNode() = default;

bool
SameElementQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    get_element_ids(_element_ids);
    bool result = !_element_ids.empty();
    _cached_evaluate_result.emplace(result);
    return result;
}

const HitList &
SameElementQueryNode::evaluateHits(HitList & hl)
{
    hl.clear();
    return hl;
}

void
SameElementQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    if (_cached_evaluate_result.has_value()) {
        element_ids = _element_ids;
        return;
    }
    if (_children.empty()) {
        return;
    }
    for (auto& child : _children) {
        if (!child->evaluate()) {
            return;
        }
    }
    _children.front()->get_element_ids(element_ids);
    std::span<const std::unique_ptr<QueryNode>> others(_children.begin() + 1, _children.end());
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
SameElementQueryNode::unpack_match_data(uint32_t docid, MatchData& match_data, const IIndexEnvironment& index_env,
                                        ElementIds element_ids)
{
    if (evaluate()) {
        if (isRanked()) {
            auto &qtd = static_cast<QueryTermData&>(getQueryItem());
            const ITermData &td = qtd.getTermData();
            unpack_match_data(docid, td, match_data, index_env, element_ids);
        }
        for (const auto& node : _children) {
            node->unpack_match_data(docid, match_data, index_env, ElementIds(_element_ids));
        }
    }
}

void
SameElementQueryNode::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data, const IIndexEnvironment&,
                                        ElementIds)
{
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

void
SameElementQueryNode::reset()
{
    for (auto& child : _children) {
        child->reset();
    }
    _cached_evaluate_result.reset();
    _element_ids.clear();
}

void
SameElementQueryNode::add_child(std::unique_ptr<QueryNode> term)
{
    _children.emplace_back(std::move(term));
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
    for (auto& child : _children) {
        child->getLeaves(tl);
    }
}

void
SameElementQueryNode::get_hidden_leaves(ConstQueryTermList & tl) const
{
    for (auto& child : _children) {
        child->getLeaves(tl);
    }
}

}
