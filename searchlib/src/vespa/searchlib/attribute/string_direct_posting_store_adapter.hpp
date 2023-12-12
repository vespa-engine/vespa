// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_direct_posting_store_adapter.h"
#include "direct_posting_store_adapter.hpp"

namespace search::attribute {

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
StringDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
StringDirectPostingStoreAdapter(const PostingStoreType& posting_store,
                                const EnumStoreType& enum_store,
                                bool attr_is_filter)
    : DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>(posting_store, enum_store, attr_is_filter)
{
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
StringDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::LookupResult
StringDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
lookup(const LookupKey& key, vespalib::datastore::EntryRef dictionary_snapshot) const
{
    vespalib::stringref keyAsString = key.asString();
    // Assert the unfortunate assumption of the comparators.
    // Should be lifted once they take the length too.
    assert(keyAsString.data()[keyAsString.size()] == '\0');
    auto comp = this->_enum_store.make_folded_comparator(keyAsString.data());
    auto find_result = this->_dict.find_posting_list(comp, dictionary_snapshot);
    if (find_result.first.valid()) {
        auto pidx = find_result.second;
        if (pidx.valid()) {
            auto minmax = this->_posting_store.getAggregated(pidx);
            return LookupResult(pidx, this->_posting_store.frozenSize(pidx), minmax.getMin(), minmax.getMax(), find_result.first);
        }
    }
    return LookupResult();
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
void
StringDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot,
               const std::function<void(vespalib::datastore::EntryRef)>& callback) const
{
    this->_dict.collect_folded(enum_idx, dictionary_snapshot, callback);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
int64_t
StringDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
get_integer_value(vespalib::datastore::EntryRef) const noexcept
{
    // This is not supported for string attributes and is never called.
    abort();
}

}
