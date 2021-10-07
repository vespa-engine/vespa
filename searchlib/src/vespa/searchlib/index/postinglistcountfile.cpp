// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistcountfile.h"

namespace search::index {

PostingListCountFileSeqRead::PostingListCountFileSeqRead()
{
}

PostingListCountFileSeqRead::~PostingListCountFileSeqRead()
{
}

void
PostingListCountFileSeqRead::
getParams(PostingListParams &params)
{
    params.clear();
}

PostingListCountFileSeqWrite::PostingListCountFileSeqWrite()
{
}

PostingListCountFileSeqWrite::~PostingListCountFileSeqWrite()
{
}

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
