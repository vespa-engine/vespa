// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "direct_posting_store_adapter.h"
#include <vespa/vespalib/datastore/entryref.h>

namespace search::attribute {

/**
 * Adapter used to implement a specific IDirectPostingStore interface for
 * a numeric attribute vector with underlying posting lists (fast-search).
 */
template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
class NumericDirectPostingStoreAdapter : public DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType> {
public:
    using LookupKey = typename ParentType::LookupKey;
    using LookupResult = typename ParentType::LookupResult;

    NumericDirectPostingStoreAdapter(const PostingStoreType& posting_store,
                                     const EnumStoreType& enum_store,
                                     bool attr_is_filter);

    LookupResult lookup(const LookupKey& key,
                        vespalib::datastore::EntryRef dictionary_snapshot) const override;
    void collect_folded(vespalib::datastore::EntryRef enum_idx, vespalib::datastore::EntryRef dictionary_snapshot,
                        const std::function<void(vespalib::datastore::EntryRef)>& callback) const override;
    int64_t get_integer_value(vespalib::datastore::EntryRef enum_idx) const noexcept override;
};

}
