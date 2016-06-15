// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.postinglistcountfile");
#include "postinglistcountfile.h"

namespace search
{

namespace index
{

PostingListCountFileSeqRead::PostingListCountFileSeqRead(void)
{
}


PostingListCountFileSeqRead::~PostingListCountFileSeqRead(void)
{
}


void
PostingListCountFileSeqRead::
getParams(PostingListParams &params)
{
    params.clear();
}


PostingListCountFileSeqWrite::PostingListCountFileSeqWrite(void)
{
}


PostingListCountFileSeqWrite::~PostingListCountFileSeqWrite(void)
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
