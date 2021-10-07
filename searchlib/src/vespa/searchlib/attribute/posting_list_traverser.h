// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/*
 * Class used to traverse a posting list and call the functor for each
 * lid.
 */
template <typename PostingList>
class PostingListTraverser
{
    using EntryRef = vespalib::datastore::EntryRef;
    const PostingList &_postingList;
    EntryRef _pidx;
public:
    PostingListTraverser(const PostingList &postingList, EntryRef pidx)
        : _postingList(postingList),
          _pidx(pidx)
    {
    }
    ~PostingListTraverser() { }

    template <typename Func>
    void
    foreach(Func func) const {
        _postingList.foreach_frozen(_pidx, func);
    }

    template <typename Func>
    void
    foreach_key(Func func) const {
        _postingList.foreach_frozen_key(_pidx, func);
    }
};

}
