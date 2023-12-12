// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include "direct_posting_store_adapter.h"
#include <cassert>

namespace search::attribute {

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
DirectPostingStoreAdapter(const PostingStoreType& posting_store,
                          const EnumStoreType& enum_store,
                          bool attr_is_filter)
    : _posting_store(posting_store),
      _enum_store(enum_store),
      _dict(enum_store.get_dictionary()),
      _attr_is_filter(attr_is_filter)
{
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
vespalib::datastore::EntryRef
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
get_dictionary_snapshot() const
{
    return _dict.get_frozen_root();
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
std::unique_ptr<queryeval::SearchIterator>
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
make_bitvector_iterator(vespalib::datastore::EntryRef posting_idx, uint32_t doc_id_limit,
                        fef::TermFieldMatchData& match_data, bool strict) const
{
    return _posting_store.make_bitvector_iterator(posting_idx, doc_id_limit, match_data, strict);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
bool
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
has_weight_iterator(vespalib::datastore::EntryRef posting_idx) const noexcept
{
    return _posting_store.has_btree(posting_idx);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
bool
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
has_bitvector(vespalib::datastore::EntryRef posting_idx) const noexcept
{
    return _posting_store.has_bitvector(posting_idx);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
void
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
create(vespalib::datastore::EntryRef posting_idx, std::vector<IteratorType>& dst) const
{
    assert(posting_idx.valid());
    _posting_store.beginFrozen(posting_idx, dst);
}

template <typename ParentType, typename PostingStoreType, typename EnumStoreType>
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::IteratorType
DirectPostingStoreAdapter<ParentType, PostingStoreType, EnumStoreType>::
create(vespalib::datastore::EntryRef posting_idx) const
{
    assert(posting_idx.valid());
    return _posting_store.beginFrozen(posting_idx);
}

}
