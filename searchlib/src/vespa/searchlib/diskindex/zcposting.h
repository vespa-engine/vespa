// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcbuf.h"
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/fastos/file.h>

namespace search {

namespace index {

class PostingListCountFileSeqRead;
class PostingListCountFileSeqWrite;

}

namespace diskindex {

class Zc4PostingSeqRead : public index::PostingListFileSeqRead
{
    Zc4PostingSeqRead(const Zc4PostingSeqRead &);
    Zc4PostingSeqRead &operator=(const Zc4PostingSeqRead &);

protected:
    typedef bitcompression::FeatureDecodeContextBE DecodeContext;
    typedef bitcompression::FeatureEncodeContextBE EncodeContext;

    DecodeContext *_decodeContext;
    uint32_t _docIdK;
    uint32_t _prevDocId;	// Previous document id
    uint32_t _numDocs;		// Documents in chunk or word
    search::ComprFileReadContext _readContext;
    FastOS_File _file;
    bool _hasMore;
    bool _dynamicK;		// Caclulate EG compression parameters ?
    uint32_t _lastDocId;	// last document in chunk or word
    uint32_t _minChunkDocs;	// # of documents needed for chunking
    uint32_t _minSkipDocs;	// # of documents needed for skipping
    uint32_t _docIdLimit;	// Limit for document ids (docId < docIdLimit)

    ZcBuf _zcDocIds;		// Document id deltas
    ZcBuf _l1Skip;		// L1 skip info
    ZcBuf _l2Skip;		// L2 skip info
    ZcBuf _l3Skip;		// L3 skip info
    ZcBuf _l4Skip;		// L4 skip info

    uint64_t _numWords;		// Number of words in file
    uint64_t _fileBitSize;
    uint32_t _chunkNo;		// Chunk number

    // Variables for validating skip information while reading
    uint32_t _l1SkipDocId;
    uint32_t _l1SkipDocIdPos;
    uint64_t _l1SkipFeaturesPos;
    uint32_t _l2SkipDocId;
    uint32_t _l2SkipDocIdPos;
    uint32_t _l2SkipL1SkipPos;
    uint64_t _l2SkipFeaturesPos;
    uint32_t _l3SkipDocId;
    uint32_t _l3SkipDocIdPos;
    uint32_t _l3SkipL1SkipPos;
    uint32_t _l3SkipL2SkipPos;
    uint64_t _l3SkipFeaturesPos;
    uint32_t _l4SkipDocId;
    uint32_t _l4SkipDocIdPos;
    uint32_t _l4SkipL1SkipPos;
    uint32_t _l4SkipL2SkipPos;
    uint32_t _l4SkipL3SkipPos;
    uint64_t _l4SkipFeaturesPos;

    // Variable for validating chunk information while reading
    uint64_t _featuresSize;
    index::PostingListCountFileSeqRead *const _countFile;

    uint64_t _headerBitLen;	// Size of file header in bits
    uint64_t _rangeEndOffset;	// End offset for word pair
    uint64_t _readAheadEndOffset;// Readahead end offset for word pair
    uint64_t _wordStart;	// last word header position
    uint64_t _checkPointPos;	// file position when checkpointing
    uint32_t _residue;		// Number of unread documents after word header
    uint32_t _checkPointChunkNo; // _chunkNo when checkpointing
    uint32_t _checkPointResidue;// _residue when checkpointing
    bool _checkPointHasMore;	// _hasMore when checkpointing
public:
    Zc4PostingSeqRead(index::PostingListCountFileSeqRead *countFile);

    ~Zc4PostingSeqRead();

    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    /**
     * Read document id and features for common word.
     */
    virtual void readCommonWordDocIdAndFeatures(DocIdAndFeatures &features);

    void readDocIdAndFeatures(DocIdAndFeatures &features) override;
    void checkPointWrite(vespalib::nbostream &out) override;
    void checkPointRead(vespalib::nbostream &in) override;
    void readCounts(const PostingListCounts &counts) override; // Fill in for next word
    bool open(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead) override;
    bool close() override;
    void getParams(PostingListParams &params) override;
    void getFeatureParams(PostingListParams &params) override;
    void readWordStartWithSkip();
    void readWordStart();
    void readHeader();
    static const vespalib::string &getIdentifier();

    // Methods used when generating posting list for common word pairs.

    /*
     * Get current posting offset, measured in bits.  First posting list
     * starts at 0, i.e.  file header is not accounted for here.
     *
     * @return current posting offset, measured in bits.
     */
    uint64_t getCurrentPostingOffset() const override;

    /**
     * Set current posting offset, measured in bits.  First posting
     * list starts at 0, i.e.  file header is not accounted for here.
     *
     * @param Offset start of posting lists for word pair.
     * @param endOffset end of posting lists for word pair.
     * @param readAheadOffset end of posting list for either this or a
     *				 later word pair, depending on disk seek cost.
     */
    void setPostingOffset(uint64_t offset, uint64_t endOffset, uint64_t readAheadOffset) override;
};


class Zc4PostingSeqWrite : public index::PostingListFileSeqWrite
{
    Zc4PostingSeqWrite(const Zc4PostingSeqWrite &);
    Zc4PostingSeqWrite &operator=(const Zc4PostingSeqWrite &);

protected:
    typedef bitcompression::FeatureEncodeContextBE EncodeContext;

    EncodeContext _encodeContext;
    search::ComprFileWriteContext _writeContext;
    FastOS_File _file;
    uint32_t _minChunkDocs;	// # of documents needed for chunking
    uint32_t _minSkipDocs;	// # of documents needed for skipping
    uint32_t _docIdLimit;	// Limit for document ids (docId < docIdLimit)
    // Unpacked document ids for word and feature sizes
    typedef std::pair<uint32_t, uint32_t> DocIdAndFeatureSize;
    std::vector<DocIdAndFeatureSize> _docIds;

    // Buffer up features in memory
    EncodeContext *_encodeFeatures;
    uint64_t _featureOffset;		// Bit offset of next feature
    search::ComprFileWriteContext _featureWriteContext;
    uint64_t _writePos;		// Bit position for start of current word
    bool _dynamicK;		// Caclulate EG compression parameters ?
    ZcBuf _zcDocIds;		// Document id deltas
    ZcBuf _l1Skip;		// L1 skip info
    ZcBuf _l2Skip;		// L2 skip info
    ZcBuf _l3Skip;		// L3 skip info
    ZcBuf _l4Skip;		// L4 skip info

    uint64_t _numWords;		// Number of words in file
    uint64_t _fileBitSize;
    index::PostingListCountFileSeqWrite *const _countFile;
public:
    Zc4PostingSeqWrite(index::PostingListCountFileSeqWrite *countFile);
    ~Zc4PostingSeqWrite();

    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    void writeDocIdAndFeatures(const DocIdAndFeatures &features) override;
    void flushWord() override;
    void checkPointWrite(vespalib::nbostream &out) override;
    void checkPointRead(vespalib::nbostream &in) override;

    bool open(const vespalib::string &name, const TuneFileSeqWrite &tuneFileWrite,
              const search::common::FileHeaderContext &fileHeaderContext) override;

    bool close() override;
    void setParams(const PostingListParams &params) override;
    void getParams(PostingListParams &params) override;
    void setFeatureParams(const PostingListParams &params) override;
    void getFeatureParams(PostingListParams &params) override;

    /**
     * Flush chunk to file.
     */
    void flushChunk();
    void calcSkipInfo();

    /**
     * Flush word with skip info to disk
     */
    void flushWordWithSkip(bool hasMore);


    /**
     * Flush word without skip info to disk.
     */
    virtual void flushWordNoSkip();

    /**
     * Prepare for next word or next chunk.
     */
    void resetWord();

    /**
     * Make header using feature encode write context.
     */
    void makeHeader(const search::common::FileHeaderContext &fileHeaderContext);
    void updateHeader();

    /**
     * Read header, using temporary feature decode context.
     */
    uint32_t readHeader(const vespalib::string &name);
};


class ZcPostingSeqRead : public Zc4PostingSeqRead
{
public:
    ZcPostingSeqRead(index::PostingListCountFileSeqRead *countFile);
    void readDocIdAndFeatures(DocIdAndFeatures &features) override;
    static const vespalib::string &getIdentifier();
};

class ZcPostingSeqWrite : public Zc4PostingSeqWrite
{
public:
    ZcPostingSeqWrite(index::PostingListCountFileSeqWrite *countFile);
    void flushWordNoSkip() override;
};

} // namespace diskindex

} // namespace search
