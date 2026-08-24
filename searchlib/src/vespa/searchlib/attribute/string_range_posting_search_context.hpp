// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_list_folded_search_context.hpp"
#include "string_range_posting_search_context.h"

#include <vespa/vespalib/datastore/infinity_unique_store_string_comparator.h>

namespace search::attribute {

template <typename BaseSC, typename AttrT, typename DataT>
StringRangePostingSearchContext<BaseSC, AttrT, DataT>::StringRangePostingSearchContext(BaseSC&&     base_sc,
                                                                                       bool         use_bit_vector,
                                                                                       const AttrT& to_be_searched)
    : Parent(std::move(base_sc), use_bit_vector, to_be_searched), _range_spec(this->get_string_range_spec()) {
    if (this->valid() && _range_spec) {
        if (!_range_spec->left_unbounded && !_range_spec->right_unbounded) {
            this->lookupRange(_enumStore.make_folded_comparator(_range_spec->left.c_str()),
                              _enumStore.make_folded_comparator(_range_spec->right.c_str()));

        } else if (!_range_spec->left_unbounded) {
            this->lookupRange(
                _enumStore.make_folded_comparator(_range_spec->left.c_str()),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()));

        } else if (!_range_spec->right_unbounded) {
            this->lookupRange(
                vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()),
                _enumStore.make_folded_comparator(_range_spec->right.c_str()));

        } else {
            this->lookupRange(
                vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()));
        }
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
