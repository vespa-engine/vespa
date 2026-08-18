// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.hpp"
#include "string_range_posting_search_context.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
StringRangePostingSearchContext<BaseSC, AttrT, DataT>::StringRangePostingSearchContext(BaseSC&&     base_sc,
                                                                                       bool         useBitVector,
                                                                                       const AttrT& toBeSearched)
    : Parent(std::move(base_sc), useBitVector, toBeSearched) {
    if (this->valid()) {
        const std::string& left = this->get_left();
        const std::string& right = this->get_right();
        auto               comp_left = _enumStore.make_folded_comparator(left.c_str());
        auto               comp_right = _enumStore.make_folded_comparator(right.c_str());
        this->lookupRange(comp_left, comp_right);
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringRangePostingSearchContext<BaseSC, AttrT, DataT>::use_dictionary_entry(
    PostingListSearchContext::DictionaryConstIterator& /*it*/) const {
    return true;
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringRangePostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const ExecuteInfo& /*info*/) const {
    // Testing
    return true;
    // return false;
}

} // namespace search::attribute
