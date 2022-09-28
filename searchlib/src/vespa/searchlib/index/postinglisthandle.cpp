// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglisthandle.h"
#include "postinglistfile.h"

namespace search::index {

search::queryeval::SearchIterator *
PostingListHandle::createIterator(const PostingListCounts &counts,
                                  const search::fef::TermFieldMatchDataArray &matchData,
                                  bool useBitVector) const
{
    return _file->createIterator(counts, *this, matchData, useBitVector);
}

}
