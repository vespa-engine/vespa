// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistcountfile.h"
#include "postinglistparams.h"

namespace search::index {

PostingListCountFileSeqRead::PostingListCountFileSeqRead() = default;
PostingListCountFileSeqRead::~PostingListCountFileSeqRead() = default;

void
PostingListCountFileSeqRead::
getParams(PostingListParams &params)
{
    params.clear();
}

PostingListCountFileSeqWrite::PostingListCountFileSeqWrite() = default;
PostingListCountFileSeqWrite::~PostingListCountFileSeqWrite() = default;

void
PostingListCountFileSeqWrite::
setParams(const PostingListParams &params)
{
    (void) params;
}

void
PostingListCountFileSeqWrite::
getParams(PostingListParams &params)
{
    params.clear();
}

}
