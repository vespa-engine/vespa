// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_writer.h"
#include "zc4_posting_reader.h"
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/fastos/file.h>
#include "zc4_posting_params.h"

namespace search::index {
    class PostingListCountFileSeqRead;
    class PostingListCountFileSeqWrite;
}

namespace search::diskindex {

class Zc4PostingSeqRead : public index::PostingListFileSeqRead
{
    Zc4PostingSeqRead(const Zc4PostingSeqRead &);
    Zc4PostingSeqRead &operator=(const Zc4PostingSeqRead &);

protected:
    Zc4PostingReader<true> _reader;
    FastOS_File _file;
    uint64_t _numWords;     // Number of words in file
    uint64_t _fileBitSize;
    index::PostingListCountFileSeqRead *const _countFile;
    uint64_t _headerBitLen;       // Size of file header in bits
public:
    Zc4PostingSeqRead(index::PostingListCountFileSeqRead *countFile, bool dynamic_k);

    ~Zc4PostingSeqRead();

    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    void readDocIdAndFeatures(DocIdAndFeatures &features) override;
    void readCounts(const PostingListCounts &counts) override; // Fill in for next word
    bool open(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead) override;
    bool close() override;
    void getParams(PostingListParams &params) override;
    void getFeatureParams(PostingListParams &params) override;
    void readHeader();
    static const vespalib::string &getIdentifier(bool dynamic_k);
};


class Zc4PostingSeqWrite : public index::PostingListFileSeqWrite
{
    Zc4PostingSeqWrite(const Zc4PostingSeqWrite &);
    Zc4PostingSeqWrite &operator=(const Zc4PostingSeqWrite &);

protected:
    typedef bitcompression::FeatureEncodeContextBE EncodeContext;

    Zc4PostingWriter<true> _writer;
    FastOS_File      _file;
    uint64_t _fileBitSize;
    index::PostingListCountFileSeqWrite *const _countFile;
    /**
     * Make header using feature encode write context.
     */
    void makeHeader(const search::common::FileHeaderContext &fileHeaderContext);
    bool updateHeader();
public:
    Zc4PostingSeqWrite(index::PostingListCountFileSeqWrite *countFile);
    ~Zc4PostingSeqWrite();

    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    void writeDocIdAndFeatures(const DocIdAndFeatures &features) override;
    void flushWord() override;

    bool open(const vespalib::string &name,
              const TuneFileSeqWrite &tuneFileWrite,
              const search::common::FileHeaderContext &fileHeaderContext) override;

    bool close() override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) override;
    void setFeatureParams(const PostingListParams &params) override;
    void getFeatureParams(PostingListParams &params) override;
};

class ZcPostingSeqWrite : public Zc4PostingSeqWrite
{
public:
    ZcPostingSeqWrite(index::PostingListCountFileSeqWrite *countFile);
};

}
