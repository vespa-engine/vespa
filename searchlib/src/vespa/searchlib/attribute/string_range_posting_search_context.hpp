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
        // In both ends, an open (exclusive) boundary uses a less-or-equal comparator while closed
        // (inclusive) boundary uses an ordinary less-than variant.  It works like this:
        // _lowerDictItr.lower_bound(low) will call low.less(candidate, boundary) finding the first
        // entry where that call returns false:
        //  - an ordinary less comparator computes "candidate < boundary", which turns false at the
        //    first candidate >= boundary, so the walk starts inclusive of the boundary entry.
        //  - a less-or-equal comparator computes "candidate <= needs", which turns false some
        //    entries later, at the first candidate > boundary, so the walk starts strictly past the
        //    boundary entry, i.e. exclusive.
        // _upperDictItr.seekPast(high) calls high.less(boundary, candidate) - the opposite order from
        // lower_bound - finding the first entry where it turns true:
        //  - an ordinary less comparator computes "boundary < candidate", which stays false while
        //    candidate <= boundary, so the walk advances past the boundary entry too, keeping it in
        //    range (inclusive upper bound).
        //  - a less-or-equal comparator computes "boundary <= candidate", which turns true one entry
        //    earlier, at the boundary entry itself, so the walk stops there instead of past it,
        //    excluding it (exclusive upper bound).
        // With that narrowing, [_lowerDictItr, _upperDictItr> will hold exactly the matching entries.
        auto make_lower = [this] {
            const char* value = _range_spec->left->c_str();
            return _range_spec->left_closed ? _enumStore.string_lookup_comparator(value)
                                            : _enumStore.string_lookup_lteq_comparator(value);
        };
        auto make_upper = [this] {
            const char* value = _range_spec->right->c_str();
            return _range_spec->right_closed ? _enumStore.string_lookup_comparator(value)
                                             : _enumStore.string_lookup_lteq_comparator(value);
        };
        if (_range_spec->left && _range_spec->right) {
            this->lookupRange(make_lower(), make_upper());
        } else if (_range_spec->left) {
            this->lookupRange(
                make_lower(),
                vespalib::datastore::PositiveInfinityUniqueStoreStringComparator<IEnumStore::InternalIndex>(
                    _enumStore.get_data_store()));

        } else if (_range_spec->right) {
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
            this->lookupSingle();
        }
    }
}

template <typename BaseSC, typename AttrT, typename DataT>
bool StringRangePostingSearchContext<BaseSC, AttrT, DataT>::use_posting_lists_when_non_strict(
    const ExecuteInfo& /*info*/) const {
    return false;
}

} // namespace search::attribute
