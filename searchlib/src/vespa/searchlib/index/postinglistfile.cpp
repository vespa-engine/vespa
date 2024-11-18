// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postinglistfile.h"
#include "postinglistparams.h"
#include <vespa/fastos/file.h>
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::index {

PostingListFileSeqRead::PostingListFileSeqRead() = default;
PostingListFileSeqRead::~PostingListFileSeqRead() = default;

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

PostingListFileSeqWrite::~PostingListFileSeqWrite() = default;

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

PostingListFileRandRead::~PostingListFileRandRead() = default;

void
PostingListFileRandRead::afterOpen(FastOS_FileInterface &file)
{
    _memoryMapped = (file.MemoryMapPtr(0) != nullptr);
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
    if (_ownLower) {
        delete _lower;
    }
}

std::unique_ptr<search::queryeval::SearchIterator>
PostingListFileRandReadPassThrough::
createIterator(const DictionaryLookupResult& lookup_result,
               const PostingListHandle& handle,
               const search::fef::TermFieldMatchDataArray &matchData) const
{
    return _lower->createIterator(lookup_result, handle, matchData);
}

PostingListHandle
PostingListFileRandReadPassThrough::read_posting_list(const DictionaryLookupResult& lookup_result)
{
    return _lower->read_posting_list(lookup_result);
}

void
PostingListFileRandReadPassThrough::consider_trim_posting_list(const DictionaryLookupResult &lookup_result,
                                                      PostingListHandle &handle) const
{
    return _lower->consider_trim_posting_list(lookup_result, handle);
}

bool
PostingListFileRandReadPassThrough::open(const std::string &name,
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

}
