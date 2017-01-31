// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/queryeval/iterators.h>
#include <vespa/fastos/dynamiclibrary.h>

namespace search {

namespace diskindex {

using bitcompression::Position;

#define ZCDECODE(valI, resop)						\
do {									\
    if (__builtin_expect(valI[0] < (1 << 7), true)) {			\
	resop valI[0];							\
	valI += 1;							\
    } else if (__builtin_expect(valI[1] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              (valI[1] << 7);						\
        valI += 2;							\
    } else if (__builtin_expect(valI[2] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              (valI[2] << 14);						\
        valI += 3;							\
    } else if (__builtin_expect(valI[3] < (1 << 7), true)) {		\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              ((valI[2] & ((1 << 7) - 1)) << 14) +			\
              (valI[3] << 21);						\
        valI += 4;							\
    } else {								\
        resop (valI[0] & ((1 << 7) - 1)) +				\
              ((valI[1] & ((1 << 7) - 1)) << 7) +			\
              ((valI[2] & ((1 << 7) - 1)) << 14) +			\
              ((valI[3] & ((1 << 7) - 1)) << 21) +			\
              (valI[4] << 28);						\
        valI += 5;							\
    }									\
} while (0)

class ZcIteratorBase : public queryeval::RankedSearchIteratorBase
{
protected:
    ZcIteratorBase(const fef::TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit);
    virtual void readWordStart(uint32_t docIdLimit) = 0;
    virtual void rewind(Position start) = 0;
    void initRange(uint32_t beginid, uint32_t endid) override;
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    Trinary is_strict() const override { return Trinary::True; }
private:
    uint32_t   _docIdLimit;
    Position   _start;
};

template <bool bigEndian>
class Zc4RareWordPostingIterator : public ZcIteratorBase
{
private:
    typedef ZcIteratorBase ParentClass;

public:
    typedef bitcompression::FeatureDecodeContext<bigEndian> DecodeContextBase;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    DecodeContextBase *_decodeContext;
    unsigned int       _residue;
    uint32_t           _prevDocId;  // Previous document id
    uint32_t           _numDocs;    // Documents in chunk or word

    Zc4RareWordPostingIterator(const fef::TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit);

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void readWordStart(uint32_t docIdLimit) override;
    void rewind(Position start) override;
};

template <bool bigEndian>
class ZcRareWordPostingIterator : public Zc4RareWordPostingIterator<bigEndian>
{
private:
    typedef Zc4RareWordPostingIterator<bigEndian> ParentClass;
    using ParentClass::getDocId;
    using ParentClass::getUnpacked;
    using ParentClass::clearUnpacked;
    using ParentClass::_residue;
    using ParentClass::setDocId;
    using ParentClass::setAtEnd;
    using ParentClass::_numDocs;

    uint32_t _docIdK;

public:
    using ParentClass::_decodeContext;
    ZcRareWordPostingIterator(const search::fef::TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit);

    void doSeek(uint32_t docId) override;
    void readWordStart(uint32_t docIdLimit) override;
};


template <bool bigEndian>
class ZcPostingIterator : public ZcIteratorBase
{
private:
    typedef ZcIteratorBase ParentClass;
    using ParentClass::getDocId;

public:
    // Pointer to compressed data
    const uint8_t *_valI;
    uint32_t _lastDocId;
    uint32_t _l1SkipDocId;
    uint32_t _l2SkipDocId;
    uint32_t _l3SkipDocId;
    uint32_t _l4SkipDocId;
    const uint8_t *_l1SkipDocIdPos;
    const uint8_t *_l1SkipValI;
    uint64_t _l1SkipFeaturePos;
    const uint8_t *_valIBase;
    const uint8_t *_l1SkipValIBase;
    const uint8_t *_l2SkipDocIdPos;
    const uint8_t *_l2SkipValI;
    uint64_t _l2SkipFeaturePos;
    const uint8_t *_l2SkipL1SkipPos;
    const uint8_t *_l2SkipValIBase;
    const uint8_t *_l3SkipDocIdPos;
    const uint8_t *_l3SkipValI;
    uint64_t _l3SkipFeaturePos;
    const uint8_t *_l3SkipL1SkipPos;
    const uint8_t *_l3SkipL2SkipPos;
    const uint8_t *_l3SkipValIBase;
    const uint8_t *_l4SkipDocIdPos;
    const uint8_t *_l4SkipValI;
    uint64_t _l4SkipFeaturePos;
    const uint8_t *_l4SkipL1SkipPos;
    const uint8_t *_l4SkipL2SkipPos;
    const uint8_t *_l4SkipL3SkipPos;

    typedef bitcompression::FeatureDecodeContext<bigEndian> DecodeContextBase;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    DecodeContextBase *_decodeContext;
    uint32_t _minChunkDocs;
    uint32_t _docIdK;
    bool     _hasMore;
    bool     _dynamicK;
    uint32_t _chunkNo;
    uint32_t _numDocs;
    uint64_t _featuresSize;
    uint64_t _featureSeekPos;
    // Start of current features block, needed for seeks
    const uint64_t *_featuresValI;
    int _featuresBitOffset;
    // Counts used for assertions
    const PostingListCounts &_counts;

    ZcPostingIterator(uint32_t minChunkDocs,
                      bool dynamicK,
                      const PostingListCounts &counts,
                      const search::fef::TermFieldMatchDataArray &matchData,
                      Position start, uint32_t docIdLimit);


    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void readWordStart(uint32_t docIdLimit) override;
    void rewind(Position start) override;
    VESPA_DLL_LOCAL void doChunkSkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL4SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL3SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL2SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL1SkipSeek(uint32_t docId);

    void featureSeek(uint64_t offset) {
        _decodeContext->_valI = _featuresValI + (_featuresBitOffset + offset) / 64;
        _decodeContext->setupBits((_featuresBitOffset + offset) & 63);
    }
};


extern template class Zc4RareWordPostingIterator<true>;
extern template class Zc4RareWordPostingIterator<false>;

extern template class ZcPostingIterator<true>;
extern template class ZcPostingIterator<false>;

extern template class ZcRareWordPostingIterator<true>;
extern template class ZcRareWordPostingIterator<false>;


} // namespace diskindex

} // namespace search

