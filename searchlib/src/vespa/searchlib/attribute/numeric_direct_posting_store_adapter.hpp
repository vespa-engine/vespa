// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_direct_posting_store_adapter.h"
#include "direct_posting_store_adapter.hpp"

namespace search::attribute {

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
NumericDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
NumericDirectPostingStoreAdapter(const PostingStoreType& posting_store,
                                 const EnumStoreType& enum_store,
                                 bool attr_is_filter)
    : DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>(posting_store, enum_store, attr_is_filter)
{
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
NumericDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::LookupResult
NumericDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
lookup(const LookupKey& key, vespalib::datastore::EntryRef dictionary_snapshot) const
{
    int64_t int_term;
    if (!key.asInteger(int_term)) {
        return LookupResult();
    }
    auto comp = this->_enum_store.make_comparator(int_term);
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
NumericDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot,
               const std::function<void(vespalib::datastore::EntryRef)>& callback) const
{
    (void) dictionary_snapshot;
    callback(enum_idx);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
int64_t
NumericDirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
get_integer_value(vespalib::datastore::EntryRef enum_idx) const noexcept
{
    return this->_enum_store.get_value(enum_idx);
}

}
