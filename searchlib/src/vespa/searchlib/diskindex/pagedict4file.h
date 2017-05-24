// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace vespalib
{

class GenericHeader;

}

namespace search
{

namespace diskindex
{

/**
 * Dictionary file containing words and counts for words.
 */
class PageDict4FileSeqRead : public index::DictionaryFileSeqRead
{
    typedef bitcompression::PostingListCountFileDecodeContext DC;
    typedef bitcompression::PageDict4SSReader SSReader;
    typedef bitcompression::PageDict4Reader Reader;

    typedef index::PostingListCounts PostingListCounts;

    Reader *_pReader;
    SSReader *_ssReader;

    DC _ssd;
    ComprFileReadContext _ssReadContext;
    FastOS_File _ssfile;

    DC _spd;
    ComprFileReadContext _spReadContext;
    FastOS_File _spfile;

    DC _pd;
    ComprFileReadContext _pReadContext;
    FastOS_File _pfile;

    uint64_t _ssFileBitSize;
    uint64_t _spFileBitSize;
    uint64_t _pFileBitSize;
    uint32_t _ssHeaderLen;
    uint32_t _spHeaderLen;
    uint32_t _pHeaderLen;

    bool _ssCompleted;
    bool _spCompleted;
    bool _pCompleted;

    uint64_t _wordNum;

    std::unique_ptr<vespalib::nbostream> _checkPointData;

    void
    readSSHeader();

    void
    readSPHeader();

    void
    readPHeader();

public:

    PageDict4FileSeqRead();

    virtual
    ~PageDict4FileSeqRead();

    /**
     * Read word and counts.  Only nonzero counts are returned. If at
     * end of dictionary then noWordNumHigh() is returned as word number.
     */
    virtual void
    readWord(vespalib::string &word,
             uint64_t &wordNum,
             PostingListCounts &counts) override;

    virtual bool open(const vespalib::string &name,
                      const TuneFileSeqRead &tuneFileRead) override;

    /**
     * Close dictionary file.
     */
    virtual bool close() override;

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.  Implies
     * flush from memory to disk, and possibly also sync to permanent
     * storage media.
     */
    virtual void
    checkPointWrite(vespalib::nbostream &out) override;

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    virtual void
    checkPointRead(vespalib::nbostream &in) override;

    /*
     * Get current parameters.
     */
    virtual void
    getParams(index::PostingListParams &params) override;
};

/**
 * Interface for dictionary file containing words and count for words.
 */
class PageDict4FileSeqWrite : public index::DictionaryFileSeqWrite
{
    typedef bitcompression::PostingListCountFileEncodeContext EC;
    typedef EC SPEC;
    typedef EC PEC;
    typedef EC SSEC;
    typedef bitcompression::PageDict4SSWriter SSWriter;
    typedef bitcompression::PageDict4SPWriter SPWriter;
    typedef bitcompression::PageDict4PWriter PWriter;

    typedef index::PostingListCounts PostingListCounts;

    PWriter *_pWriter;
    SPWriter *_spWriter;
    SSWriter *_ssWriter;

    EC _pe;
    ComprFileWriteContext _pWriteContext;
    FastOS_File _pfile;

    EC _spe;
    ComprFileWriteContext _spWriteContext;
    FastOS_File _spfile;

    EC _sse;
    ComprFileWriteContext _ssWriteContext;
    FastOS_File _ssfile;

    uint32_t _pHeaderLen;  // Length of header for page file (bytes)
    uint32_t _spHeaderLen; // Length of header for sparse page file (bytes)
    uint32_t _ssHeaderLen; // Length of header for sparse sparse file (bytes)

    std::unique_ptr<vespalib::nbostream> _checkPointData;

    void
    writeIndexNames(vespalib::GenericHeader &header);

    void
    writeSSSubHeader(vespalib::GenericHeader &header);

    void
    makePHeader(const search::common::FileHeaderContext &fileHeaderContext);

    void
    makeSPHeader(const search::common::FileHeaderContext &fileHeaderContext);

    void
    makeSSHeader(const search::common::FileHeaderContext &fileHeaderContext);

    void
    updatePHeader(uint64_t fileBitSize);

    void
    updateSPHeader(uint64_t fileBitSize);

    void
    updateSSHeader(uint64_t fileBitSize);

public:
    PageDict4FileSeqWrite();

    virtual
    ~PageDict4FileSeqWrite();

    /**
     * Write word and counts.  Only nonzero counts should be supplied.
     */
    virtual void
    writeWord(const vespalib::stringref &word,
              const PostingListCounts &counts) override;

    /**
     * Open dictionary file for sequential write.  The index with most
     * words should be first for optimal compression.
     */
    virtual bool
    open(const vespalib::string &name,
         const TuneFileSeqWrite &tuneFileWrite,
         const search::common::FileHeaderContext &fileHeaderContext) override;

    /**
     * Close dictionary file.
     */
    virtual bool
    close() override;

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.  Implies
     * flush from memory to disk, and possibly also sync to permanent
     * storage media.
     */
    virtual void
    checkPointWrite(vespalib::nbostream &out) override;

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    virtual void
    checkPointRead(vespalib::nbostream &in) override;

    /*
     * Set parameters.
     */
    virtual void
    setParams(const index::PostingListParams &params) override;

    /*
     * Get current parameters.
     */
    virtual void
    getParams(index::PostingListParams &params) override;
};


} // namespace diskindex

} // namespace search

