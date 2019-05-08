// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace search::diskindex {

class ZcPosOccRandRead : public index::PostingListFileRandRead
{
protected:
    std::unique_ptr<FastOS_FileInterface> _file;
    uint64_t         _fileSize;

    uint32_t _minChunkDocs; // # of documents needed for chunking
    uint32_t _minSkipDocs;  // # of documents needed for skipping
    uint32_t _docIdLimit;   // Limit for document ids (docId < docIdLimit)

    uint64_t _numWords;     // Number of words in file
    uint64_t _fileBitSize;
    uint64_t _headerBitSize;
    bitcompression::PosOccFieldsParams _fieldsParams;
    bool _dynamicK;
    bool _decode_cheap_features;


public:
    ZcPosOccRandRead();
    ~ZcPosOccRandRead();

    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListHandle PostingListHandle;

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    queryeval::SearchIterator *
    createIterator(const PostingListCounts &counts, const PostingListHandle &handle,
                   const fef::TermFieldMatchDataArray &matchData, bool usebitVector) const override;

    /**
     * Read (possibly partial) posting list into handle.
     */
    void readPostingList(const PostingListCounts &counts, uint32_t firstSegment,
                         uint32_t numSegments, PostingListHandle &handle) override;

    bool open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead) override;
    bool close() override;
    virtual void readHeader();
    static const vespalib::string &getIdentifier();
    static const vespalib::string &getSubIdentifier();
};

class Zc4PosOccRandRead : public ZcPosOccRandRead
{
public:
    Zc4PosOccRandRead();

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    queryeval::SearchIterator *
    createIterator(const PostingListCounts &counts, const PostingListHandle &handle,
                   const fef::TermFieldMatchDataArray &matchData, bool usebitVector) const override;

    void readHeader() override;

    static const vespalib::string &getIdentifier();
    static const vespalib::string &getSubIdentifier();
};


}
