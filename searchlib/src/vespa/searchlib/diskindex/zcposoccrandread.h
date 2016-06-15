// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace search
{

namespace diskindex
{

class ZcPosOccRandRead : public index::PostingListFileRandRead
{
protected:
    FastOS_File _file;
    uint64_t	     _fileSize;

    uint32_t _minChunkDocs;	// # of documents needed for chunking
    uint32_t _minSkipDocs;	// # of documents needed for skipping
    uint32_t _docIdLimit;	// Limit for document ids (docId < docIdLimit)

    uint64_t _numWords;		// Number of words in file
    uint64_t _fileBitSize;
    uint64_t _headerBitSize;
    bitcompression::PosOccFieldsParams _fieldsParams;
    bool _dynamicK;


public:
    ZcPosOccRandRead(void);

    virtual
    ~ZcPosOccRandRead(void);

    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListHandle PostingListHandle;

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    virtual search::queryeval::SearchIterator *
    createIterator(const PostingListCounts &counts,
                   const PostingListHandle &handle,
                   const search::fef::TermFieldMatchDataArray &matchData,
                   bool usebitVector) const;

    /**
     * Read (possibly partial) posting list into handle.
     */
    virtual void
    readPostingList(const PostingListCounts &counts,
                    uint32_t firstSegment,
                    uint32_t numSegments,
                    PostingListHandle &handle);

    /**
     * Open posting list file for random read.
     */
    virtual bool
    open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead);

    /**
     * Close posting list file.
     */
    virtual bool
    close(void);

    virtual void
    readHeader(void);

    static const vespalib::string &
    getIdentifier(void);

    static const vespalib::string &
    getSubIdentifier(void);
};

class Zc4PosOccRandRead : public ZcPosOccRandRead
{
public:
    Zc4PosOccRandRead(void);

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    virtual search::queryeval::SearchIterator *
    createIterator(const PostingListCounts &counts,
                   const PostingListHandle &handle,
                   const search::fef::TermFieldMatchDataArray &matchData,
                   bool usebitVector) const;

    virtual void
    readHeader(void);

    static const vespalib::string &
    getIdentifier(void);

    static const vespalib::string &
    getSubIdentifier(void);
};


} // namespace diskindex

} // namespace search

