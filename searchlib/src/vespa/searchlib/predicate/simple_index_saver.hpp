// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_index_saver.h"
#include "nbo_write.h"

namespace search::predicate {

template <typename Posting, typename Key, typename DocId>
SimpleIndexSaver<Posting, Key, DocId>::SimpleIndexSaver(const Dictionary& dictionary, const BTreeStore& btree_posting_lists, std::unique_ptr<PostingSaver<Posting>> subsaver)
    : _dictionary(dictionary),
      _btree_posting_lists(btree_posting_lists),
      _subsaver(std::move(subsaver))
{
}

template <typename Posting, typename Key, typename DocId>
SimpleIndexSaver<Posting, Key, DocId>::~SimpleIndexSaver() = default;

template <typename Posting, typename Key, typename DocId>
void
SimpleIndexSaver<Posting, Key, DocId>::save(BufferWriter& writer) const
{
    assert(sizeof(Key) <= sizeof(uint64_t));
    assert(sizeof(DocId) <= sizeof(uint32_t));
    nbo_write<uint32_t>(writer, _dictionary.size());
    for (auto it = _dictionary.begin(); it.valid(); ++it) {
        vespalib::datastore::EntryRef ref = it.getData();
        auto posting_it = _btree_posting_lists.begin(ref);
        nbo_write<uint32_t>(writer, posting_it.size());  // 0 if !valid()
        if (!posting_it.valid())
            continue;
        nbo_write<uint64_t>(writer, it.getKey());  // Key
        for (; posting_it.valid(); ++posting_it) {
            nbo_write<uint32_t>(writer, posting_it.getKey());  // DocId
            _subsaver->save(posting_it.getData(), writer);
        }
    }
}

}
