// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/objects/nbostream.h>
#include "postinglistcounts.h"

namespace search::index {

void swap(PostingListCounts & a, PostingListCounts & b)
{
    a.swap(b);
}

using vespalib::nbostream;

nbostream &
operator<<(nbostream &out, const PostingListCounts::Segment &segment)
{
    out << segment._bitLength << segment._numDocs << segment._lastDoc;
    return out;
}


nbostream &
operator>>(nbostream &in, PostingListCounts::Segment &segment)
{
    in >> segment._bitLength >> segment._numDocs >> segment._lastDoc;
    return in;
}


nbostream &
operator<<(nbostream &out, const PostingListCounts &counts)
{
    out << counts._numDocs << counts._bitLength;
    size_t numSegments = counts._segments.size();
    out << numSegments;
    for (size_t seg = 0; seg < numSegments; ++seg) {
        out << counts._segments[seg];
    }
    return out;
}


nbostream &
operator>>(nbostream &in, PostingListCounts &counts)
{
    in >> counts._numDocs >> counts._bitLength;
    size_t numSegments = 0;
    in >> numSegments;
    counts._segments.reserve(numSegments);
    counts._segments.clear();
    for (size_t seg = 0; seg < numSegments; ++seg) {
        PostingListCounts::Segment segment;
        in >> segment;
        counts._segments.push_back(segment);
    }
    return in;
}


nbostream &
operator<<(nbostream &out, const PostingListOffsetAndCounts &offsetAndCounts)
{
    out << offsetAndCounts._offset;
    out << offsetAndCounts._accNumDocs;
    out << offsetAndCounts._counts;
    return out;
}


nbostream &
operator>>(nbostream &in, PostingListOffsetAndCounts &offsetAndCounts)
{
    in >> offsetAndCounts._offset;
    in >> offsetAndCounts._accNumDocs;
    in >> offsetAndCounts._counts;
    return in;
}

}
