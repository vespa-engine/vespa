// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/*
 * Class used to traverse a posting list and call the functor for each lid.
 */
template <typename PostingStore>
class PostingListTraverser
{
    using EntryRef = vespalib::datastore::EntryRef;
    const PostingStore &_posting_store;
    EntryRef _pidx;
public:
    PostingListTraverser(const PostingStore &posting_store, EntryRef pidx)
        : _posting_store(posting_store),
          _pidx(pidx)
    {
    }
    ~PostingListTraverser() { }

    template <typename Func>
    void
    foreach(Func func) const {
        _posting_store.foreach_frozen(_pidx, func);
    }

    template <typename Func>
    void
    foreach_key(Func func) const {
        _posting_store.foreach_frozen_key(_pidx, func);
    }
};

}
