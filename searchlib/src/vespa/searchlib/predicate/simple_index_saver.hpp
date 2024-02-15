// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_index_saver.h"
#include "nbo_write.h"

namespace search::predicate {

template <typename Posting, typename Key, typename DocId>
SimpleIndexSaver<Posting, Key, DocId>::SimpleIndexSaver(Dictionary dictionary, const BTreeStore& btree_posting_lists, std::unique_ptr<PostingSaver<Posting>> subsaver)
    : _dictionary(std::move(dictionary)),
      _frozen_roots(),
      _btree_posting_lists(btree_posting_lists),
      _subsaver(std::move(subsaver))
{
    make_frozen_roots();
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
    auto& allocator = _btree_posting_lists.getAllocator();
    auto frozen_roots_it = _frozen_roots.begin();
    using PostingIterator = BTreeStore::ConstIterator;
    for (auto it = _dictionary.begin(); it.valid(); ++it, ++frozen_roots_it) {
        vespalib::datastore::EntryRef ref = it.getData();
        /*
         * Use copy of frozen root if valid, otherwise use ref from
         * frozen dictionary.
         */
        auto posting_it = frozen_roots_it->valid() ? PostingIterator(*frozen_roots_it, allocator) : _btree_posting_lists.begin(ref);
        nbo_write<uint32_t>(writer, posting_it.size());  // 0 if !valid()
        if (!posting_it.valid())
            continue;
        nbo_write<uint64_t>(writer, it.getKey());  // Key
        for (; posting_it.valid(); ++posting_it) {
            nbo_write<uint32_t>(writer, posting_it.getKey());  // DocId
            _subsaver->save(posting_it.getData(), writer);
        }
    }
    assert(frozen_roots_it == _frozen_roots.end());
}

template <typename Posting, typename Key, typename DocId>
void
SimpleIndexSaver<Posting, Key, DocId>::make_frozen_roots()
{
    /*
     * Compensate for lacking snapshot property in
     * vespalib::btree::BTreeStore.  Traverse frozen dictionary in writer
     * thread and make a copy of frozen btree roots.
     */
    _frozen_roots.reserve(_dictionary.size());
    for (auto it = _dictionary.begin(); it.valid(); ++it) {
        auto ref = it.getData();
        if (ref.valid() && _btree_posting_lists.isBTree(ref)) {
            _frozen_roots.emplace_back(_btree_posting_lists.getTreeEntry(ref)->getFrozenRootRelaxed());
            assert(_frozen_roots.back().valid());
        } else {
            _frozen_roots.emplace_back();
        }
    }
}

}
