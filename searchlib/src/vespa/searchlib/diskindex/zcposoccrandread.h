// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    static constexpr size_t decode_prefetch_size = 16;

public:
    ZcPosOccRandRead();
    ~ZcPosOccRandRead();

    using DictionaryLookupResult = index::DictionaryLookupResult;
    using PostingListCounts = index::PostingListCounts;
    using PostingListHandle = index::PostingListHandle;

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     */
    std::unique_ptr<queryeval::SearchIterator>
    createIterator(const DictionaryLookupResult& lookup_result, const PostingListHandle& handle,
                   const fef::TermFieldMatchDataArray &matchData) const override;

    /**
     * Read (possibly partial) posting list into handle.
     */
    PostingListHandle read_posting_list(const DictionaryLookupResult& lookup_result) override;
    void consider_trim_posting_list(const DictionaryLookupResult& lookup_result, PostingListHandle& handle) const override;

    bool open(const std::string &name, const TuneFileRandRead &tuneFileRead) override;
    bool close() override;
    template <typename DecodeContext>
    void readHeader(const std::string &identifier);
    virtual void readHeader();
    static const std::string &getIdentifier();
    static const std::string &getSubIdentifier();
    const index::FieldLengthInfo &get_field_length_info() const override;
};

class Zc4PosOccRandRead : public ZcPosOccRandRead
{
    using ZcPosOccRandRead::readHeader;
public:
    Zc4PosOccRandRead();

    void readHeader() override;

    static const std::string &getIdentifier();
    static const std::string &getSubIdentifier();
};


}
