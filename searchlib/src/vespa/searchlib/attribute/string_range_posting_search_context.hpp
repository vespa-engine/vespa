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
        // An open (exclusive) boundary uses a less-or-equal comparator instead of an ordinary
        // less-than one: driving the dictionary walk with it skips past the entries equal to the
        // boundary value, narrowing the walk to exactly the (half-)open range instead of relying
        // on use_dictionary_entry()'s match() filtering to exclude them after the fact.
        //
        // With that narrowing, [_lowerDictItr, _upperDictItr> should hold exactly the matching
        // entries, which would make the match() call in use_dictionary_entry() below redundant -
        // it runs a folded compare per unique value in the range, in both
        // calc_estimated_hits_in_range() and fill_array_or_bitvector(). It is kept for now
        // because the narrowing compares with the enum store's folded comparator (derived from
        // the dictionary config) while match() compares with StringRangeSearchHelper (derived
        // from the attribute's match config), and those are separate config fields. Whether they
        // can actually disagree - and hence whether the filtering is needed at all - needs
        // further investigation; if they cannot, drop the use_dictionary_entry() override and
        // let the base implementation accept every entry in the range.
        auto make_lower = [this] {
            return _range_spec->left_closed
                       ? _enumStore.make_folded_comparator(_range_spec->left.c_str())
                       : _enumStore.make_folded_comparator_less_or_equal(_range_spec->left.c_str());
        };
        auto make_upper = [this] {
            return _range_spec->right_closed
                       ? _enumStore.make_folded_comparator(_range_spec->right.c_str())
                       : _enumStore.make_folded_comparator_less_or_equal(_range_spec->right.c_str());
        };
        if (!_range_spec->left_unbounded && !_range_spec->right_unbounded) {
            this->lookupRange(make_lower(), make_upper());

        } else if (!_range_spec->left_unbounded) {
            this->lookupRange(
                make_lower(),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()));

        } else if (!_range_spec->right_unbounded) {
            this->lookupRange(
                vespalib::datastore::NegativeInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()),
                make_upper());

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
