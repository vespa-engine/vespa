// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_saver.h"
#include "simple_index.h"
#include <vespa/vespalib/stllike/allocator.h>

namespace search::predicate {

/*
 * Class used to save a SimpleIndex instance, streaming the serialized
 * data via a BufferWriter.
 */
template <typename Posting,
          typename Key = uint64_t, typename DocId = uint32_t>
class SimpleIndexSaver : public ISaver
{
    using EntryRef = vespalib::datastore::EntryRef;
    using Source = SimpleIndex<Posting,Key,DocId>;
    using Dictionary = Source::Dictionary::FrozenView;
    using FrozenRoots = std::vector<EntryRef, vespalib::allocator_large<EntryRef>>;
    using BTreeStore = Source::BTreeStore;

    const Dictionary  _dictionary;
    FrozenRoots       _frozen_roots;
    const BTreeStore& _btree_posting_lists;
    std::unique_ptr<PostingSaver<Posting>> _subsaver;

    void make_frozen_roots();
public:
    SimpleIndexSaver(Dictionary dictionary, const BTreeStore& btree_posting_lists, std::unique_ptr<PostingSaver<Posting>> _subsaver);
    ~SimpleIndexSaver() override;
    void save(BufferWriter& writer) const override;
};

}
