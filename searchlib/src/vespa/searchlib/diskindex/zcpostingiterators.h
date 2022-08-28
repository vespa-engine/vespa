// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/queryeval/iterators.h>

namespace search::diskindex {

using bitcompression::Position;

#define ZCDECODE(valI, resop)                                \
do {                                                         \
    if (__builtin_expect(valI[0] < (1 << 7), true)) {        \
    resop valI[0];                                           \
    valI += 1;                                               \
    } else if (__builtin_expect(valI[1] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              (valI[1] << 7);                                \
        valI += 2;                                           \
    } else if (__builtin_expect(valI[2] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              (valI[2] << 14);                               \
        valI += 3;                                           \
    } else if (__builtin_expect(valI[3] < (1 << 7), true)) { \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              ((valI[2] & ((1 << 7) - 1)) << 14) +           \
              (valI[3] << 21);                               \
        valI += 4;                                           \
    } else {                                                 \
        resop (valI[0] & ((1 << 7) - 1)) +                   \
              ((valI[1] & ((1 << 7) - 1)) << 7) +            \
              ((valI[2] & ((1 << 7) - 1)) << 14) +           \
              ((valI[3] & ((1 << 7) - 1)) << 21) +           \
              (valI[4] << 28);                               \
        valI += 5;                                           \
    }                                                        \
} while (0)

class ZcIteratorBase : public queryeval::RankedSearchIteratorBase
{
protected:
    ZcIteratorBase(fef::TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit);
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
class ZcRareWordPostingIteratorBase : public ZcIteratorBase
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
    bool               _decode_normal_features;
    bool               _decode_interleaved_features;
    bool               _unpack_normal_features;
    bool               _unpack_interleaved_features;
    uint32_t           _field_length;
    uint32_t           _num_occs;

    ZcRareWordPostingIteratorBase(fef::TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                                  bool decode_normal_features, bool decode_interleaved_features,
                                  bool unpack_normal_features, bool unpack_interleaved_features);

    void doUnpack(uint32_t docId) override;
    void rewind(Position start) override;
};

template <bool dynamic_k> class ZcPostingDocIdKParam;

template <>
class ZcPostingDocIdKParam<false>
{
public:
    ZcPostingDocIdKParam() { }
    constexpr static uint32_t get_doc_id_k() { return K_VALUE_ZCPOSTING_DELTA_DOCID; }
    void setup(uint32_t, uint32_t) { }
};

template <>
class ZcPostingDocIdKParam<true>
{
    uint32_t _doc_id_k;
public:
    ZcPostingDocIdKParam() : _doc_id_k(0) { }
    uint32_t get_doc_id_k() const { return _doc_id_k; }
    void setup(uint32_t num_docs, uint32_t doc_id_limit) {
        using EC = bitcompression::FeatureEncodeContext<true>;
        _doc_id_k = EC::calcDocIdK(num_docs, doc_id_limit);
    }
};


template <bool bigEndian, bool dynamic_k>
class ZcRareWordPostingIterator : public ZcRareWordPostingIteratorBase<bigEndian>
{
    using ParentClass = ZcRareWordPostingIteratorBase<bigEndian>;
    using ParentClass::getDocId;
    using ParentClass::getUnpacked;
    using ParentClass::clearUnpacked;
    using ParentClass::_residue;
    using ParentClass::setDocId;
    using ParentClass::setAtEnd;
    using ParentClass::_numDocs;
    using ParentClass::_decode_normal_features;
    using ParentClass::_decode_interleaved_features;
    using ParentClass::_unpack_normal_features;
    using ParentClass::_unpack_interleaved_features;
    using ParentClass::_field_length;
    using ParentClass::_num_occs;
    ZcPostingDocIdKParam<dynamic_k> _doc_id_k_param;
public:
    using ParentClass::_decodeContext;
    ZcRareWordPostingIterator(fef::TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                              bool decode_normal_features, bool decode_interleaved_features,
                              bool unpack_normal_features, bool unpack_interleaved_features);
    void doSeek(uint32_t docId) override;
    void readWordStart(uint32_t docIdLimit) override;
};

class ZcPostingIteratorBase : public ZcIteratorBase
{
protected:
    const uint8_t *_valI;     // docid deltas
    const uint8_t *_valIBase; // start of docid deltas
    uint64_t _featureSeekPos;

    // Helper class for L1 skip info
    class L1Skip
    {
    public:
        uint32_t _skipDocId;
        const uint8_t *_valI;
        const uint8_t *_docIdPos;
        uint64_t _skipFeaturePos;
        const uint8_t *_valIBase;

        L1Skip()
            : _skipDocId(0),
              _valI(nullptr),
              _docIdPos(nullptr),
              _skipFeaturePos(0),
              _valIBase(nullptr)
        {
        }

        void setup(uint32_t prevDocId, uint32_t lastDocId, const uint8_t *&bcompr, uint32_t skipSize) {
            if (skipSize != 0) {
                _valI = _valIBase = bcompr;
                bcompr += skipSize;
                _skipDocId = prevDocId + 1;
                ZCDECODE(_valI, _skipDocId +=);
            } else {
                _valI = _valIBase = nullptr;
                _skipDocId = lastDocId;
            }
            _skipFeaturePos = 0;
        }
        void postSetup(const ZcPostingIteratorBase &l0) {
            _docIdPos = l0._valIBase;
        }
        void decodeSkipEntry(bool decode_normal_features) {
            ZCDECODE(_valI, _docIdPos += 1 +);
            if (decode_normal_features) {
                ZCDECODE(_valI, _skipFeaturePos += 1 +);
            }
        }
        void nextDocId() {
            ZCDECODE(_valI, _skipDocId += 1 +);
        }
    };

    // Helper class for L2 skip info
    class L2Skip : public L1Skip
    {
    public:
        const uint8_t *_l1Pos;

        L2Skip()
            : L1Skip(),
              _l1Pos(nullptr)
        {
        }

        void postSetup(const L1Skip &l1) {
            _docIdPos = l1._docIdPos;
            _l1Pos = l1._valIBase;
        }
        void decodeSkipEntry(bool decode_normal_features) {
            L1Skip::decodeSkipEntry(decode_normal_features);
            ZCDECODE(_valI, _l1Pos += 1 + );
        }
    };

    // Helper class for L3 skip info
    class L3Skip : public L2Skip
    {
    public:
        const uint8_t *_l2Pos;

        L3Skip()
            : L2Skip(),
              _l2Pos(nullptr)
        {
        }

        void postSetup(const L2Skip &l2) {
            _docIdPos = l2._docIdPos;
            _l1Pos = l2._l1Pos;
            _l2Pos = l2._valIBase;
        }
        void decodeSkipEntry(bool decode_normal_features) {
            L2Skip::decodeSkipEntry(decode_normal_features);
            ZCDECODE(_valI, _l2Pos += 1 + );
        }
    };

    // Helper class for L4 skip info
    class L4Skip : public L3Skip
    {
    public:
        const uint8_t *_l3Pos;

        L4Skip()
            : L3Skip(),
              _l3Pos(nullptr)
        {
        }

        void postSetup(const L3Skip &l3) {
            _docIdPos = l3._docIdPos;
            _l1Pos = l3._l1Pos;
            _l2Pos = l3._l2Pos;
            _l3Pos = l3._valIBase;
        }

        void decodeSkipEntry(bool decode_normal_features) {
            L3Skip::decodeSkipEntry(decode_normal_features);
            ZCDECODE(_valI, _l3Pos += 1 + );
        }
    };

    // Helper class for chunk skip info
    class ChunkSkip {
    public:
        uint32_t _lastDocId;

        ChunkSkip()
            : _lastDocId(0)
        {
        }
    };

    L1Skip _l1;
    L2Skip _l2;
    L3Skip _l3;
    L4Skip _l4;
    ChunkSkip _chunk;
    uint64_t _featuresSize;
    bool     _hasMore;
    bool     _decode_normal_features;
    bool     _decode_interleaved_features;
    bool     _unpack_normal_features;
    bool     _unpack_interleaved_features;
    uint32_t _chunkNo;
    uint32_t _field_length;
    uint32_t _num_occs;

    void nextDocId(uint32_t prevDocId) {
        uint32_t docId = prevDocId + 1;
        ZCDECODE(_valI, docId +=);
        setDocId(docId);
        if (_decode_interleaved_features) {
            ZCDECODE(_valI, _field_length = 1 +);
            ZCDECODE(_valI, _num_occs = 1 +);
        }
    }
    virtual void featureSeek(uint64_t offset) = 0;
    VESPA_DLL_LOCAL void doChunkSkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL4SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL3SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL2SkipSeek(uint32_t docId);
    VESPA_DLL_LOCAL void doL1SkipSeek(uint32_t docId);
    void doSeek(uint32_t docId) override;
public:
    ZcPostingIteratorBase(fef::TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                          bool decode_normal_features, bool decode_interleaved_features,
                          bool unpack_normal_features, bool unpack_interleaved_features);
};

template <bool bigEndian>
class ZcPostingIterator : public ZcPostingIteratorBase
{
private:
    typedef ZcPostingIteratorBase ParentClass;
    using ParentClass::getDocId;

public:

    typedef bitcompression::FeatureDecodeContext<bigEndian> DecodeContextBase;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::PostingListCounts PostingListCounts;
    DecodeContextBase *_decodeContext;
    uint32_t _minChunkDocs;
    uint32_t _docIdK;
    bool     _dynamicK;
    uint32_t _numDocs;
    // Start of current features block, needed for seeks
    const uint64_t *_featuresValI;
    int _featuresBitOffset;
    // Counts used for assertions
    const PostingListCounts &_counts;

    ZcPostingIterator(uint32_t minChunkDocs, bool dynamicK, const PostingListCounts &counts,
                      search::fef::TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                      bool decode_normal_features, bool decode_interleaved_features,
                      bool unpack_normal_features, bool unpack_interleaved_features);


    void doUnpack(uint32_t docId) override;
    void readWordStart(uint32_t docIdLimit) override;
    void rewind(Position start) override;

    void featureSeek(uint64_t offset) override {
        _decodeContext->_valI = _featuresValI + (_featuresBitOffset + offset) / 64;
        _decodeContext->setupBits((_featuresBitOffset + offset) & 63);
    }
};


extern template class ZcRareWordPostingIterator<false, false>;
extern template class ZcRareWordPostingIterator<false, true>;
extern template class ZcRareWordPostingIterator<true, false>;
extern template class ZcRareWordPostingIterator<true, true>;

extern template class ZcPostingIterator<true>;
extern template class ZcPostingIterator<false>;

}
