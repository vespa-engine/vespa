// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "equiv_query_node.h"
#include "phrase_query_node.h"
#include "query_visitor.h"
#include "queryterm.hpp"

using search::common::ElementIds;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;
using search::fef::ITermFieldData;

namespace search::streaming {

namespace {

class HitWithFieldLength : public Hit
{
    uint32_t _field_length;
public:
    HitWithFieldLength(const Hit& hit, uint32_t field_length) noexcept
        : Hit(hit),
          _field_length(field_length)
    {
    }
    uint32_t get_field_length() const noexcept { return _field_length; }
};

template <typename HitType>
void merge_hits_from_children(std::vector<HitType>& hl, const MultiTerm& mt)
{
    HitList sub_hl_store;
    for (auto& subterm : mt.get_terms()) {
        auto *phrase = dynamic_cast<PhraseQueryNode*>(subterm.get());
        QueryTerm& fl_term = (phrase == nullptr) ? *subterm : *phrase->get_terms().front();
        auto& sub_hl = subterm->evaluateHits(sub_hl_store);
        for (auto& h : sub_hl) {
            if constexpr (std::is_same_v<Hit,HitType>) {
                hl.emplace_back(h);
            } else {
                hl.emplace_back(h, extract_field_length(fl_term, h.field_id()));
            }
        }
    }
    std::sort(hl.begin(), hl.end());
    auto last = std::unique(hl.begin(), hl.end(), [](auto& lhs, auto &rhs) noexcept { return lhs.at_same_pos(rhs); });
    hl.erase(last, hl.end());
}

}

EquivQueryNode::EquivQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, uint32_t num_terms)
    : MultiTerm(std::move(result_base), "", num_terms)
{
}

EquivQueryNode::~EquivQueryNode() = default;

bool
EquivQueryNode::evaluate()
{
    if (_cached_evaluate_result.has_value()) {
        return _cached_evaluate_result.value();
    }
    bool result = false;
    for (auto& subterm : get_terms()) {
        if (subterm->evaluate()) {
            result = true;
            break;
        }
    }
    _cached_evaluate_result.emplace(result);
    return result;
}

const HitList &
EquivQueryNode::evaluateHits(HitList & hl)
{
    hl.clear();
    merge_hits_from_children(hl, *this);
    return hl;
}

void
EquivQueryNode::get_element_ids(std::vector<uint32_t>& element_ids)
{
    HitList hit_list;
    merge_hits_from_children(hit_list, *this);
    get_element_ids_helper(element_ids, hit_list);
}

void
EquivQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                                  ElementIds element_ids)
{
    std::vector<HitWithFieldLength> hit_list;
    merge_hits_from_children(hit_list, *this);
    unpack_match_data_helper(docid, td, match_data, hit_list, *this, is_filter(), index_env, element_ids);
}

bool
EquivQueryNode::multi_index_terms() const noexcept
{
    return true;
}

const EquivQueryNode*
EquivQueryNode::as_equiv_query_node() const noexcept
{
    return this;
}

std::vector<std::unique_ptr<QueryTerm>>
EquivQueryNode::steal_terms()
{
    return std::move(_terms);
}

void
EquivQueryNode::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
