// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_index.h"

namespace search::predicate {

/*
 * Class used to save a SimpleIndex instance, streaming the serialized
 * data via a BufferWriter.
 */
template <typename Posting,
          typename Key = uint64_t, typename DocId = uint32_t>
class SimpleIndexSaver
{
    using Source = SimpleIndex<Posting,Key,DocId>;
    using Dictionary = Source::Dictionary;
    using BTreeStore = Source::BTreeStore;

    const Dictionary& _dictionary;
    const BTreeStore& _btree_posting_lists;
    std::unique_ptr<PostingSaver<Posting>> _subsaver;
public:
    SimpleIndexSaver(const Dictionary& dictionary, const BTreeStore& btree_posting_lists, std::unique_ptr<PostingSaver<Posting>> _subsaver);
    ~SimpleIndexSaver();
    void save(BufferWriter& writer) const;
};

}
