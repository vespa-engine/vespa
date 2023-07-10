// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include "zc4_posting_params.h"

namespace search::diskindex {

class ZcPosOccRandRead : public index::PostingListFileRandRead
{
protected:
    std::unique_ptr<FastOS_FileInterface> _file;
    uint64_t         _fileSize;
    Zc4PostingParams _posting_params;
    uint64_t _numWords;     // Number of words in file
    uint64_t _fileBitSize;
    uint64_t _headerBitSize;
    bitcompression::PosOccFieldsParams _fieldsParams;

public:
    ZcPosOccRandRead();
    ~ZcPosOccRandRead();

    using PostingListCounts = index::PostingListCounts;
    using PostingListHandle = index::PostingListHandle;

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    std::unique_ptr<queryeval::SearchIterator>
    createIterator(const PostingListCounts &counts, const PostingListHandle &handle,
                   const fef::TermFieldMatchDataArray &matchData, bool usebitVector) const override;

    /**
     * Read (possibly partial) posting list into handle.
     */
    void readPostingList(const PostingListCounts &counts, uint32_t firstSegment,
                         uint32_t numSegments, PostingListHandle &handle) override;

    bool open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead) override;
    bool close() override;
    template <typename DecodeContext>
    void readHeader(const vespalib::string &identifier);
    virtual void readHeader();
    static const vespalib::string &getIdentifier();
    static const vespalib::string &getSubIdentifier();
    const index::FieldLengthInfo &get_field_length_info() const override;
};

class Zc4PosOccRandRead : public ZcPosOccRandRead
{
    using ZcPosOccRandRead::readHeader;
public:
    Zc4PosOccRandRead();

    void readHeader() override;

    static const vespalib::string &getIdentifier();
    static const vespalib::string &getSubIdentifier();
};


}
