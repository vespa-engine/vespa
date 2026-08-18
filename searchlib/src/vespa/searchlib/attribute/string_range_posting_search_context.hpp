// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.hpp"
#include "string_range_posting_search_context.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
StringRangePostingSearchContext<BaseSC, AttrT, DataT>::StringRangePostingSearchContext(BaseSC&&     base_sc,
                                                                                       bool         use_bit_vector,
                                                                                       const AttrT& to_be_searched)
    : Parent(std::move(base_sc), use_bit_vector, to_be_searched), _range_spec(this->get_string_range_spec()) {
    if (this->valid() && _range_spec) {
        const std::string& left = _range_spec->left;
        const std::string& right = _range_spec->right;
        auto               comp_left = _enumStore.make_folded_comparator(left.c_str());
        auto               comp_right = _enumStore.make_folded_comparator(right.c_str());
        this->lookupRange(comp_left, comp_right);
        if (this->_uniqueValues == 1u) {
            if (!this->_lowerDictItr.valid() || use_single_dictionary_entry(this->_lowerDictItr)) {
                this->lookupSingle();
            } else {
                this->_uniqueValues = 0;
            }
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringRangePostingSearchContext<BaseSC, AttrT, DataT>::use_dictionary_entry(
    PostingListSearchContext::DictionaryConstIterator& it) const {
    if (this->match(_enumStore.get_value(it.getKey().load_acquire()))) {
        return true;
    }
    ++it;
    return false;
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringRangePostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const ExecuteInfo& /*info*/) const {
    return false;
}

} // namespace search::attribute
