// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.postinglistfile");
#include "postinglistfile.h"

namespace search
{

namespace index
{

PostingListFileSeqRead::PostingListFileSeqRead()
    : _counts(),
      _residueDocs(0)
{
}


PostingListFileSeqRead::~PostingListFileSeqRead()
{
}


void
PostingListFileSeqRead::
getParams(PostingListParams &params)
{
    params.clear();
}


void
PostingListFileSeqRead::
setFeatureParams(const PostingListParams &params)
{
    (void) params;
}


void
PostingListFileSeqRead::
getFeatureParams(PostingListParams &params)
{
    params.clear();
}


PostingListFileSeqWrite::PostingListFileSeqWrite()
    : _counts()
{
}


PostingListFileSeqWrite::~PostingListFileSeqWrite()
{
}


void
PostingListFileSeqWrite::
setParams(const PostingListParams &params)
{
    (void) params;
}


void
PostingListFileSeqWrite::
getParams(PostingListParams &params)
{
    params.clear();
}


void
PostingListFileSeqWrite::
setFeatureParams(const PostingListParams &params)
{
    (void) params;
}


void
PostingListFileSeqWrite::
getFeatureParams(PostingListParams &params)
{
    params.clear();
}


PostingListFileRandRead::
PostingListFileRandRead()
    : _memoryMapped(false)
{
}


PostingListFileRandRead::~PostingListFileRandRead()
{
}


void
PostingListFileRandRead::afterOpen(FastOS_FileInterface &file)
{
    _memoryMapped = file.MemoryMapPtr(0) != NULL;
}


PostingListFileRandReadPassThrough::
PostingListFileRandReadPassThrough(PostingListFileRandRead *lower,
                                   bool ownLower)
    : _lower(lower),
      _ownLower(ownLower)
{
}


PostingListFileRandReadPassThrough::~PostingListFileRandReadPassThrough()
{
    if (_ownLower)
        delete _lower;
}


search::queryeval::SearchIterator *
PostingListFileRandReadPassThrough::
createIterator(const PostingListCounts &counts,
               const PostingListHandle &handle,
               const search::fef::TermFieldMatchDataArray &matchData,
               bool usebitVector) const
{
    return _lower->createIterator(counts, handle, matchData, usebitVector);
}


void
PostingListFileRandReadPassThrough::
readPostingList(const PostingListCounts &counts,
                uint32_t firstSegment,
                uint32_t numSegments,
                PostingListHandle &handle)
{
    _lower->readPostingList(counts, firstSegment, numSegments,
                            handle);
}


bool
PostingListFileRandReadPassThrough::open(const vespalib::string &name,
        const TuneFileRandRead &tuneFileRead)
{
    bool ret = _lower->open(name, tuneFileRead);
    _memoryMapped = _lower->getMemoryMapped();
    return ret;
}


bool
PostingListFileRandReadPassThrough::close()
{
    return _lower->close();
}


} // namespace index

} // namespace search
