// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search
{

namespace diskindex
{

class PageDict4RandRead : public index::DictionaryFileRandRead
{
    typedef bitcompression::PostingListCountFileDecodeContext DC;
    typedef bitcompression::PageDict4SSReader SSReader;

    typedef bitcompression::PageDict4SSLookupRes SSLookupRes;
    typedef bitcompression::PageDict4SPLookupRes SPLookupRes;
    typedef bitcompression::PageDict4PLookupRes PLookupRes;
    typedef bitcompression::PageDict4PageParams PageDict4PageParams;

    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListOffsetAndCounts PostingListOffsetAndCounts;

    SSReader *_ssReader;

    DC _ssd;
    ComprFileReadContext _ssReadContext;
    FastOS_File _ssfile;
    FastOS_File _spfile;
    FastOS_File _pfile;

    uint64_t _ssFileBitSize;
    uint64_t _spFileBitSize;
    uint64_t _pFileBitSize;
    uint32_t _ssHeaderLen;
    uint32_t _spHeaderLen;
    uint32_t _pHeaderLen;

    void
    readSSHeader();

    void
    readSPHeader(void);

    void
    readPHeader(void);

public:
    PageDict4RandRead(void);

    virtual
    ~PageDict4RandRead(void);

    virtual bool
    lookup(const vespalib::stringref &word,
           uint64_t &wordNum,
           PostingListOffsetAndCounts &offsetAndCounts);

    /**
     * Open dictionary file for random read.
     */
    virtual bool open(const vespalib::string &name,
                      const TuneFileRandRead &tuneFileRead);

    /**
     * Close dictionary file.
     */
    virtual bool close(void);

    virtual uint64_t
    getNumWordIds(void) const;
};


} // namespace diskindex

} // namespace search



