// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>
#include <vespa/fastos/file.h>

namespace vespalib { class GenericHeader; }

namespace search::diskindex {

/**
 * Dictionary file containing words and counts for words.
 */
class PageDict4FileSeqRead : public index::DictionaryFileSeqRead
{
    using DC = bitcompression::PostingListCountFileDecodeContext;
    using SSReader = bitcompression::PageDict4SSReader;
    using Reader = bitcompression::PageDict4Reader;
    using PostingListCounts = index::PostingListCounts;
    struct DictFileReadContext;

    std::unique_ptr<Reader>   _pReader;
    std::unique_ptr<SSReader> _ssReader;
    std::unique_ptr<DictFileReadContext> _ss;
    std::unique_ptr<DictFileReadContext> _sp;
    std::unique_ptr<DictFileReadContext> _p;
    uint64_t _wordNum;
public:
    PageDict4FileSeqRead();
    ~PageDict4FileSeqRead() override;

    /**
     * Read word and counts.  Only nonzero counts are returned. If at
     * end of dictionary then noWordNumHigh() is returned as word number.
     */
    void readWord(vespalib::string &word, uint64_t &wordNum, PostingListCounts &counts) override;
    bool open(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead) override;
    bool close() override;
    void getParams(index::PostingListParams &params) override;
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
    using FileHeaderContext = common::FileHeaderContext;

    std::unique_ptr<PWriter>  _pWriter;
    std::unique_ptr<SPWriter> _spWriter;
    std::unique_ptr<SSWriter> _ssWriter;

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

    void writeSSSubHeader(vespalib::GenericHeader &header);
    void makePHeader(const FileHeaderContext &fileHeaderContext);
    void makeSPHeader(const FileHeaderContext &fileHeaderContext);
    void makeSSHeader(const FileHeaderContext &fileHeaderContext);
    bool updatePHeader(uint64_t fileBitSize);
    bool updateSPHeader(uint64_t fileBitSize);
    bool updateSSHeader(uint64_t fileBitSize);
public:
    PageDict4FileSeqWrite();
    ~PageDict4FileSeqWrite();

    void writeWord(vespalib::stringref word, const PostingListCounts &counts) override;

    /**
     * Open dictionary file for sequential write.  The index with most
     * words should be first for optimal compression.
     */
    bool open(const vespalib::string &name, const TuneFileSeqWrite &tuneFileWrite,
              const FileHeaderContext &fileHeaderContext) override;

    bool close() override;
    void setParams(const index::PostingListParams &params) override;
    void getParams(index::PostingListParams &params) override;
};

}
