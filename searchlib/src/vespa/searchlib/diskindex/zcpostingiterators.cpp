// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcpostingiterators.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search {

namespace diskindex {

using search::fef::TermFieldMatchDataArray;
using search::bitcompression::FeatureDecodeContext;
using search::bitcompression::FeatureEncodeContext;
using queryeval::RankedSearchIteratorBase;

#define DEBUG_ZCPOSTING_PRINTF 0
#define DEBUG_ZCPOSTING_ASSERT 0

ZcIteratorBase::ZcIteratorBase(const TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit) :
    RankedSearchIteratorBase(matchData),
    _docIdLimit(docIdLimit),
    _start(start)
{ }

void
ZcIteratorBase::initRange(uint32_t beginid, uint32_t endid)
{
    uint32_t prev = getDocId();
    setEndId(endid);
    if ((beginid <= prev) || (prev == 0)) {
        rewind(_start);
        readWordStart(getDocIdLimit());
    }
    seek(beginid);
}


template <bool bigEndian>
Zc4RareWordPostingIterator<bigEndian>::
Zc4RareWordPostingIterator(const TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit)
    : ZcIteratorBase(matchData, start, docIdLimit),
      _decodeContext(NULL),
      _residue(0),
      _prevDocId(0),
      _numDocs(0)
{ }


template <bool bigEndian>
void
Zc4RareWordPostingIterator<bigEndian>::doSeek(uint32_t docId)
{
    typedef FeatureEncodeContext<bigEndian> EC;
    uint32_t length;
    uint64_t val64;

    uint32_t oDocId = getDocId();

    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    if (getUnpacked()) {
        clearUnpacked();
        if (__builtin_expect(--_residue == 0, false))
            goto atbreak;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DELTA_DOCID, EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    while (__builtin_expect(oDocId < docId, true)) {
        UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
        _decodeContext->skipFeatures(1);
        UC64_DECODECONTEXT_LOAD(o, _decodeContext->_);
        if (__builtin_expect(--_residue == 0, false))
            goto atbreak;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DELTA_DOCID, EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
    setDocId(oDocId);
    return;
 atbreak:
    setAtEnd(); // Mark end of data
    return;
}


template <bool bigEndian>
void
Zc4RareWordPostingIterator<bigEndian>::doUnpack(uint32_t docId)
{
    if (!_matchData.valid() || getUnpacked())
        return;
    assert(docId == getDocId());
    _decodeContext->unpackFeatures(_matchData, docId);
    setUnpacked();
}

template <bool bigEndian>
void Zc4RareWordPostingIterator<bigEndian>::rewind(Position start)
{
    _decodeContext->setPosition(start);
}

template <bool bigEndian>
void
Zc4RareWordPostingIterator<bigEndian>::readWordStart(uint32_t docIdLimit)
{
    (void) docIdLimit;
    typedef FeatureEncodeContext<bigEndian> EC;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    uint32_t length;
    uint64_t val64;

    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);

    _numDocs = static_cast<uint32_t>(val64) + 1;
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DELTA_DOCID, EC);
    uint32_t docId = static_cast<uint32_t>(val64) + 1;
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);

    setDocId(docId);
    _residue = _numDocs;
    clearUnpacked();
}


template <bool bigEndian>
ZcRareWordPostingIterator<bigEndian>::
ZcRareWordPostingIterator(const TermFieldMatchDataArray &matchData, Position start, uint32_t docIdLimit)
    : Zc4RareWordPostingIterator<bigEndian>(matchData, start, docIdLimit),
      _docIdK(0)
{
}


template <bool bigEndian>
void
ZcRareWordPostingIterator<bigEndian>::doSeek(uint32_t docId)
{
    typedef FeatureEncodeContext<bigEndian> EC;
    uint32_t length;
    uint64_t val64;

    uint32_t oDocId = getDocId();

    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    if (getUnpacked()) {
        clearUnpacked();
        if (__builtin_expect(--_residue == 0, false))
            goto atbreak;
        UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    while (__builtin_expect(oDocId < docId, true)) {
        UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
        _decodeContext->skipFeatures(1);
        UC64_DECODECONTEXT_LOAD(o, _decodeContext->_);
        if (__builtin_expect(--_residue == 0, false))
            goto atbreak;
        UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
    }
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
    setDocId(oDocId);
    return;
 atbreak:
    setAtEnd();   // Mark end of data
    return;
}


template <bool bigEndian>
void
ZcRareWordPostingIterator<bigEndian>::readWordStart(uint32_t docIdLimit)
{
    typedef FeatureEncodeContext<bigEndian> EC;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    uint32_t length;
    uint64_t val64;

    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    _numDocs = static_cast<uint32_t>(val64) + 1;
    _docIdK = EC::calcDocIdK(_numDocs, docIdLimit);
    UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
    uint32_t docId = static_cast<uint32_t>(val64) + 1;
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);

    setDocId(docId);
    _residue = _numDocs;
    clearUnpacked();
}


template <bool bigEndian>
ZcPostingIterator<bigEndian>::
ZcPostingIterator(uint32_t minChunkDocs,
                  bool dynamicK,
                  const PostingListCounts &counts,
                  const search::fef::TermFieldMatchDataArray &matchData,
                  Position start, uint32_t docIdLimit)
    : ZcIteratorBase(matchData, start, docIdLimit),
      _valI(NULL),
      _lastDocId(0),
      _l1SkipDocId(0),
      _l2SkipDocId(0),
      _l3SkipDocId(0),
      _l4SkipDocId(0),
      _l1SkipDocIdPos(NULL),
      _l1SkipValI(NULL),
      _l1SkipFeaturePos(0),
      _valIBase(NULL),
      _l1SkipValIBase(NULL),
      _l2SkipDocIdPos(NULL),
      _l2SkipValI(NULL),
      _l2SkipFeaturePos(0),
      _l2SkipL1SkipPos(NULL),
      _l2SkipValIBase(NULL),
      _l3SkipDocIdPos(NULL),
      _l3SkipValI(NULL),
      _l3SkipFeaturePos(0),
      _l3SkipL1SkipPos(NULL),
      _l3SkipL2SkipPos(NULL),
      _l3SkipValIBase(NULL),
      _l4SkipDocIdPos(NULL),
      _l4SkipValI(NULL),
      _l4SkipFeaturePos(0),
      _l4SkipL1SkipPos(NULL),
      _l4SkipL2SkipPos(NULL),
      _l4SkipL3SkipPos(NULL),
      _decodeContext(NULL),
      _minChunkDocs(minChunkDocs),
      _docIdK(0),
      _hasMore(false),
      _dynamicK(dynamicK),
      _chunkNo(0),
      _numDocs(0),
      _featuresSize(0),
      _featureSeekPos(0),
      _featuresValI(NULL),
      _featuresBitOffset(0),
      _counts(counts)
{ }


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::readWordStart(uint32_t docIdLimit)
{
    typedef FeatureEncodeContext<bigEndian> EC;
    DecodeContextBase &d = *_decodeContext;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;

    uint32_t prevDocId = _hasMore ? _lastDocId : 0u;
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);

    _numDocs = static_cast<uint32_t>(val64) + 1;
    bool hasMore = false;
    if (__builtin_expect(_numDocs >= _minChunkDocs, false)) {
        if (bigEndian) {
            hasMore = static_cast<int64_t>(oVal) < 0;
            oVal <<= 1;
            length = 1;
        } else {
            hasMore = (oVal & 1) != 0;
            oVal >>= 1;
            length = 1;
        }
        UC64_READBITS_NS(o, EC);
    }
    if (_dynamicK)
        _docIdK = EC::calcDocIdK((_hasMore || hasMore) ? 1 : _numDocs, docIdLimit);
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DOCIDSSIZE, EC);
    uint32_t docIdsSize = val64 + 1;
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L1SKIPSIZE, EC);
    uint32_t l1SkipSize = val64;
    uint32_t l2SkipSize = 0;
    if (l1SkipSize != 0) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L2SKIPSIZE, EC);
        l2SkipSize = val64;
    }
    uint32_t l3SkipSize = 0;
    if (l2SkipSize != 0) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L3SKIPSIZE, EC);
        l3SkipSize = val64;
    }
    uint32_t l4SkipSize = 0;
    if (l3SkipSize != 0) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L4SKIPSIZE, EC);
        l4SkipSize = val64;
    }
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FEATURESSIZE, EC);
    _featuresSize = val64;
    if (_dynamicK) {
        UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
    } else {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_LASTDOCID, EC);
    }
    _lastDocId = docIdLimit - 1 - val64;
    if (_hasMore || hasMore) {
        if (!_counts._segments.empty()) {
            assert(_lastDocId == _counts._segments[_chunkNo]._lastDoc);
        }
    }

    uint64_t bytePad = oPreRead & 7;
    if (bytePad > 0) {
        length = bytePad;
        UC64_READBITS_NS(o, EC);
    }

    UC64_DECODECONTEXT_STORE(o, d._);
    assert((d.getBitOffset() & 7) == 0);
    const uint8_t *bcompr = d.getByteCompr();
    _valIBase = _valI = bcompr;
    _l1SkipDocIdPos = _l2SkipDocIdPos = bcompr;
    _l3SkipDocIdPos = _l4SkipDocIdPos = bcompr;
    bcompr += docIdsSize;
    if (l1SkipSize != 0) {
        _l1SkipValIBase = _l1SkipValI = bcompr;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = bcompr;
        bcompr += l1SkipSize;
    } else {
        _l1SkipValIBase = _l1SkipValI = NULL;
        _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos = NULL;
    }
    if (l2SkipSize != 0) {
        _l2SkipValIBase = _l2SkipValI = bcompr;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = bcompr;
        bcompr += l2SkipSize;
    } else {
        _l2SkipValIBase = _l2SkipValI = NULL;
        _l3SkipL2SkipPos = _l4SkipL2SkipPos = NULL;
    }
    if (l3SkipSize != 0) {
        _l3SkipValIBase = _l3SkipValI = bcompr;
        _l4SkipL3SkipPos = bcompr;
        bcompr += l3SkipSize;
    } else {
        _l3SkipValIBase = _l3SkipValI = NULL;
        _l4SkipL3SkipPos = NULL;
    }
    if (l4SkipSize != 0) {
        _l4SkipValI = bcompr;
        bcompr += l4SkipSize;
    } else {
        _l4SkipValI = NULL;
    }
    d.setByteCompr(bcompr);
    _hasMore = hasMore;
    // Save information about start of next chunk
    _featuresValI = d.getCompr();
    _featuresBitOffset = d.getBitOffset();
    _l1SkipFeaturePos = _l2SkipFeaturePos = 0;
    _l3SkipFeaturePos = _l4SkipFeaturePos = 0;
    _featureSeekPos = 0;
    clearUnpacked();
    // Unpack first docid delta in chunk
    uint32_t oDocId = prevDocId;
    ZCDECODE(_valI, oDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
    printf("Decode docId=%d\n",
           oDocId);
#endif
    setDocId(oDocId);
    // Unpack first L1 Skip info docid delta
    if (_l1SkipValI != NULL) {
        _l1SkipDocId = prevDocId;
        ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
    } else
        _l1SkipDocId = _lastDocId;
    // Unpack first L2 skip info docid delta
    if (_l2SkipValI != NULL) {
        _l2SkipDocId = prevDocId;
        ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
    } else
        _l2SkipDocId = _lastDocId;
    // Unpack first L3 skip info docid delta
    if (_l3SkipValI != NULL) {
        _l3SkipDocId = prevDocId;
        ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 +);
    } else
        _l3SkipDocId = _lastDocId;
    // Unpack first L4 skip info docid delta
    if (_l4SkipValI != NULL) {
        _l4SkipDocId = prevDocId;
        ZCDECODE(_l4SkipValI, _l4SkipDocId += 1 +);
    } else
        _l4SkipDocId = _lastDocId;
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doChunkSkipSeek(uint32_t docId)
{
    while (docId > _lastDocId && _hasMore) {
        // Skip to start of next chunk
        _featureSeekPos = 0;
        featureSeek(_featuresSize);
        _chunkNo++;
        readWordStart(getDocIdLimit());	// Read word start for next chunk
    }
    if (docId > _lastDocId) {
        _l4SkipDocId = _l3SkipDocId = _l2SkipDocId = _l1SkipDocId = search::endDocId;
        setAtEnd();
    }
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doL4SkipSeek(uint32_t docId)
{
    uint32_t lastL4SkipDocId;

    if (__builtin_expect(docId > _lastDocId, false)) {
        doChunkSkipSeek(docId);
        if (docId <= _l4SkipDocId)
            return;
    }
    do {
        lastL4SkipDocId = _l4SkipDocId;
        ZCDECODE(_l4SkipValI, _l4SkipDocIdPos += 1 +);
        ZCDECODE(_l4SkipValI, _l4SkipFeaturePos += 1 +);
        ZCDECODE(_l4SkipValI, _l4SkipL1SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipL2SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipL3SkipPos += 1 + );
        ZCDECODE(_l4SkipValI, _l4SkipDocId += 1 + );
#if DEBUG_ZCPOSTING_PRINTF
        printf("L4Decode docId %d, docIdPos %d,"
               "l1SkipPos %d, l2SkipPos %d, l3SkipPos %d, nextDocId %d\n",
               lastL4SkipDocId,
               (int) (_l4SkipDocIdPos - _valIBase),
               (int) (_l4SkipL1SkipPos - _l1SkipValIBase),
               (int) (_l4SkipL2SkipPos - _l2SkipValIBase),
               (int) (_l4SkipL3SkipPos - _l3SkipValIBase),
               _l4SkipDocId);
#endif
    } while (docId > _l4SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos = _l3SkipDocIdPos =
            _l4SkipDocIdPos;
    _l1SkipFeaturePos = _l2SkipFeaturePos = _l3SkipFeaturePos =
                        _l4SkipFeaturePos;
    _l1SkipDocId = _l2SkipDocId = _l3SkipDocId = lastL4SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos = _l3SkipL1SkipPos = _l4SkipL1SkipPos;
    _l2SkipValI = _l3SkipL2SkipPos = _l4SkipL2SkipPos;
    _l3SkipValI = _l4SkipL3SkipPos;
    ZCDECODE(_valI, lastL4SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
    ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
    ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
    printf("L4Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d L3SkipPos %d, nextDocId %d\n",
           lastL4SkipDocId,
           (int) (_l4SkipDocIdPos - _valIBase),
           (int) (_l4SkipL1SkipPos - _l1SkipValIBase),
           (int) (_l4SkipL2SkipPos - _l2SkipValIBase),
           (int) (_l4SkipL3SkipPos - _l3SkipValIBase),
           _l4SkipDocId);
#endif
    setDocId(lastL4SkipDocId);
    _featureSeekPos = _l4SkipFeaturePos;
    clearUnpacked();
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doL3SkipSeek(uint32_t docId)
{
    uint32_t lastL3SkipDocId;

    if (__builtin_expect(docId > _l4SkipDocId, false)) {
        doL4SkipSeek(docId);
        if (docId <= _l3SkipDocId)
            return;
    }
    do {
        lastL3SkipDocId = _l3SkipDocId;
        ZCDECODE(_l3SkipValI, _l3SkipDocIdPos += 1 +);
        ZCDECODE(_l3SkipValI, _l3SkipFeaturePos += 1 +);
        ZCDECODE(_l3SkipValI, _l3SkipL1SkipPos += 1 + );
        ZCDECODE(_l3SkipValI, _l3SkipL2SkipPos += 1 + );
        ZCDECODE(_l3SkipValI, _l3SkipDocId += 1 + );
#if DEBUG_ZCPOSTING_PRINTF
        printf("L3Decode docId %d, docIdPos %d,"
               "l1SkipPos %d, l2SkipPos %d, nextDocId %d\n",
               lastL3SkipDocId,
               (int) (_l3SkipDocIdPos - _valIBase),
               (int) (_l3SkipL1SkipPos - _l1SkipValIBase),
               (int) (_l3SkipL2SkipPos - _l2SkipValIBase),
               _l3SkipDocId);
#endif
    } while (docId > _l3SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos = _l3SkipDocIdPos;
    _l1SkipFeaturePos = _l2SkipFeaturePos = _l3SkipFeaturePos;
    _l1SkipDocId = _l2SkipDocId = lastL3SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos = _l3SkipL1SkipPos;
    _l2SkipValI = _l3SkipL2SkipPos;
    ZCDECODE(_valI, lastL3SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
    ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
    printf("L3Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d, nextDocId %d\n",
           lastL3SkipDocId,
           (int) (_l3SkipDocIdPos - _valIBase),
           (int) (_l3SkipL1SkipPos - _l1SkipValIBase),
           (int) (_l3SkipL2SkipPos - _l2SkipValIBase),
           _l3SkipDocId);
#endif
    setDocId(lastL3SkipDocId);
    _featureSeekPos = _l3SkipFeaturePos;
    clearUnpacked();
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doL2SkipSeek(uint32_t docId)
{
    uint32_t lastL2SkipDocId;

    if (__builtin_expect(docId > _l3SkipDocId, false)) {
        doL3SkipSeek(docId);
        if (docId <= _l2SkipDocId)
            return;
    }
    do {
        lastL2SkipDocId = _l2SkipDocId;
        ZCDECODE(_l2SkipValI, _l2SkipDocIdPos += 1 +);
        ZCDECODE(_l2SkipValI, _l2SkipFeaturePos += 1 +);
        ZCDECODE(_l2SkipValI, _l2SkipL1SkipPos += 1 + );
        ZCDECODE(_l2SkipValI, _l2SkipDocId += 1 + );
#if DEBUG_ZCPOSTING_PRINTF
        printf("L2Decode docId %d, docIdPos %d, l1SkipPos %d, nextDocId %d\n",
               lastL2SkipDocId,
               (int) (_l2SkipDocIdPos - _valIBase),
               (int) (_l2SkipL1SkipPos - _l1SkipValIBase),
               _l2SkipDocId);
#endif
    } while (docId > _l2SkipDocId);
    _valI = _l1SkipDocIdPos = _l2SkipDocIdPos;
    _l1SkipFeaturePos = _l2SkipFeaturePos;
    _l1SkipDocId = lastL2SkipDocId;
    _l1SkipValI = _l2SkipL1SkipPos;
    ZCDECODE(_valI, lastL2SkipDocId += 1 +);
    ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
    printf("L2Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL2SkipDocId,
           (int) (_l2SkipDocIdPos - _valIBase),
           (int) (_l2SkipL1SkipPos - _l1SkipValIBase),
           _l2SkipDocId);
#endif
    setDocId(lastL2SkipDocId);
    _featureSeekPos = _l2SkipFeaturePos;
    clearUnpacked();
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doL1SkipSeek(uint32_t docId)
{
    uint32_t lastL1SkipDocId;
    if (__builtin_expect(docId > _l2SkipDocId, false)) {
        doL2SkipSeek(docId);
        if (docId <= _l1SkipDocId)
            return;
    }
    do {
        lastL1SkipDocId = _l1SkipDocId;
        ZCDECODE(_l1SkipValI, _l1SkipDocIdPos += 1 +);
        ZCDECODE(_l1SkipValI, _l1SkipFeaturePos += 1 +);
        ZCDECODE(_l1SkipValI, _l1SkipDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
        printf("L1Decode docId %d, docIdPos %d, L1SkipPos %d, nextDocId %d\n",
               lastL1SkipDocId,
               (int) (_l1SkipDocIdPos - _valIBase),
               (int) (_l1SkipValI - _l1SkipValIBase),
                _l1SkipDocId);
#endif
    } while (docId > _l1SkipDocId);
    _valI = _l1SkipDocIdPos;
    ZCDECODE(_valI, lastL1SkipDocId += 1 +);
    setDocId(lastL1SkipDocId);
#if DEBUG_ZCPOSTING_PRINTF
    printf("L1SkipSeek, docId %d docIdPos %d, nextDocId %d\n",
           lastL1SkipDocId,
           (int) (_l1SkipDocIdPos - _valIBase),
           _l1SkipDocId);
#endif
    _featureSeekPos = _l1SkipFeaturePos;
    clearUnpacked();
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doSeek(uint32_t docId)
{
    if (docId > _l1SkipDocId) {
        doL1SkipSeek(docId);
    }
    uint32_t oDocId = getDocId();
#if DEBUG_ZCPOSTING_ASSERT
    assert(oDocId <= _l1SkipDocId);
    assert(docId <= _l1SkipDocId);
    assert(oDocId <= _l2SkipDocId);
    assert(docId <= _l2SkipDocId);
    assert(oDocId <= _l3SkipDocId);
    assert(docId <= _l3SkipDocId);
    assert(oDocId <= _l4SkipDocId);
    assert(docId <= _l4SkipDocId);
#endif
    const uint8_t *oCompr = _valI;
    while (__builtin_expect(oDocId < docId, true)) {
#if DEBUG_ZCPOSTING_ASSERT
        assert(oDocId <= _l1SkipDocId);
        assert(oDocId <= _l2SkipDocId);
        assert(oDocId <= _l3SkipDocId);
        assert(oDocId <= _l4SkipDocId);
#endif
        ZCDECODE(oCompr, oDocId += 1 +);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
        incNeedUnpack();
    }
    _valI = oCompr;
    setDocId(oDocId);
    return;
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doUnpack(uint32_t docId)
{
    if (!_matchData.valid() || getUnpacked())
        return;
    if (_featureSeekPos != 0) {
        // Handle deferred feature position seek now.
        featureSeek(_featureSeekPos);
        _featureSeekPos = 0;
    }
    assert(docId == getDocId());
    uint32_t needUnpack = getNeedUnpack();
    if (needUnpack > 1)
        _decodeContext->skipFeatures(needUnpack - 1);
    _decodeContext->unpackFeatures(_matchData, docId);
    setUnpacked();
}

template <bool bigEndian>
void ZcPostingIterator<bigEndian>::rewind(Position start)
{
    _decodeContext->setPosition(start);
    _hasMore = false;
    _lastDocId = 0;
    _chunkNo = 0;
}


template class Zc4RareWordPostingIterator<true>;
template class Zc4RareWordPostingIterator<false>;

template class ZcPostingIterator<true>;
template class ZcPostingIterator<false>;

template class ZcRareWordPostingIterator<true>;
template class ZcRareWordPostingIterator<false>;

} // namespace diskindex

} // namespace search
