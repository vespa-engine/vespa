// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.hpp"
#include "string_posting_search_context.h"

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
StringPostingSearchContext<BaseSC, AttrT, DataT>::StringPostingSearchContext(BaseSC&& base_sc, bool useBitVector,
                                                                             const AttrT& toBeSearched)
    : Parent(std::move(base_sc), useBitVector, toBeSearched) {
    LookupAdapter adapter(*this);
    if (matcher().setup_posting_sc(_enumStore, adapter)) {
        if (this->_uniqueValues == 1u) {
            /*
             * A single dictionary entry from lookupRange() might not be
             * a match if this is a regex search or a fuzzy search.
             */
            if (!this->_lowerDictItr.valid() || use_single_dictionary_entry(this->_lowerDictItr)) {
                this->lookupSingle();
            } else {
                this->_uniqueValues = 0;
            }
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringPostingSearchContext<BaseSC, AttrT, DataT>::use_dictionary_entry(
    PostingListSearchContext::DictionaryConstIterator& it) const {
    return matcher().use_dictionary_entry(_enumStore, it);
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringPostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const ExecuteInfo& info) const {
    return matcher().use_posting_lists_when_non_strict(info, this->_docIdLimit);
}

} // namespace search::attribute
