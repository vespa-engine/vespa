// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.postinglistcountfile");
#include "postinglistcountfile.h"

namespace search
{

namespace index
{

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


} // namespace index

} // namespace search
