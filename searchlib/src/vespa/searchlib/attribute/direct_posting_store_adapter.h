// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"
#include <vespa/vespalib/datastore/entryref.h>
#include <vector>

namespace search { class IEnumStoreDictionary; }

namespace search::attribute {

/**
 * Base adapter class used to implement a specific IDirectPostingStore interface for
 * an attribute vector with underlying posting lists (fast-search).
 */
template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
class DirectPostingStoreAdapter : public ParentType {
protected:
    const PostingStoreType& _posting_store;
    const EnumStoreType& _enum_store;
    const IEnumStoreDictionary& _dict;
    bool _attr_is_filter;

public:
    using IteratorType = typename ParentType::IteratorType;

    DirectPostingStoreAdapter(const PostingStoreType& posting_store,
                              const EnumStoreType& enum_store,
                              bool attr_is_filter);

    vespalib::datastore::EntryRef get_dictionary_snapshot() const override;
    bool has_weight_iterator(vespalib::datastore::EntryRef posting_idx) const noexcept override;
    std::unique_ptr<queryeval::SearchIterator> make_bitvector_iterator(vespalib::datastore::EntryRef posting_idx, uint32_t doc_id_limit,
                                                                       fef::TermFieldMatchData& match_data, bool strict) const override;
    bool has_bitvector(vespalib::datastore::EntryRef posting_idx) const noexcept override;
    int64_t get_integer_value(vespalib::datastore::EntryRef enum_idx) const noexcept override;

    void create(vespalib::datastore::EntryRef idx, std::vector<IteratorType>& dst) const override;
    IteratorType create(vespalib::datastore::EntryRef idx) const override;
    bool has_always_weight_iterator() const noexcept override { return !_attr_is_filter; }
};

}
