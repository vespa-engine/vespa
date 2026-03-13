// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "features_size_flush.h"
#include "zcpostingiterators.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <cassert>

namespace search::diskindex {

using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchData;
using search::bitcompression::FeatureDecodeContext;
using search::bitcompression::FeatureEncodeContext;
using queryeval::RankedSearchIteratorBase;

#define DEBUG_ZCPOSTING_PRINTF 0
#define DEBUG_ZCPOSTING_ASSERT 0

ZcIteratorBase::ZcIteratorBase(TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit) :
    RankedSearchIteratorBase(std::move(matchData)),
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
ZcRareWordPostingIteratorBase<bigEndian>::
ZcRareWordPostingIteratorBase(TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                              bool decode_normal_features, bool decode_interleaved_features,
                              bool unpack_normal_features, bool unpack_interleaved_features)
    : ZcIteratorBase(std::move(matchData), start, docIdLimit),
      _decodeContext(nullptr),
      _residue(0),
      _prevDocId(0),
      _numDocs(0),
      _decode_normal_features(decode_normal_features),
      _decode_interleaved_features(decode_interleaved_features),
      _unpack_normal_features(unpack_normal_features),
      _unpack_interleaved_features(unpack_interleaved_features),
      _field_length(0),
      _num_occs(0)
{ }


template <bool bigEndian, bool dynamic_k>
ZcRareWordPostingIterator<bigEndian, dynamic_k>::
ZcRareWordPostingIterator(TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                          bool decode_normal_features, bool decode_interleaved_features,
                          bool unpack_normal_features, bool unpack_interleaved_features)
    : ZcRareWordPostingIteratorBase<bigEndian>(std::move(matchData), start, docIdLimit,
                                               decode_normal_features, decode_interleaved_features,
                                               unpack_normal_features, unpack_interleaved_features),
      _doc_id_k_param()
{
}

template <bool bigEndian, bool dynamic_k>
void
ZcRareWordPostingIterator<bigEndian, dynamic_k>::doSeek(uint32_t docId)
{
    using EC = FeatureEncodeContext<bigEndian>;
    uint32_t length;
    uint64_t val64;

    uint32_t oDocId = getDocId();

    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    if (getUnpacked()) {
        clearUnpacked();
        if (__builtin_expect(--_residue == 0, false)) {
            goto atbreak;
        }
        UC64_DECODEEXPGOLOMB_NS(o, _doc_id_k_param.get_doc_id_k(), EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
        if (_decode_interleaved_features) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FIELD_LENGTH, EC);
            _field_length = static_cast<uint32_t>(val64) + 1;
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUM_OCCS, EC);
            _num_occs = static_cast<uint32_t>(val64) + 1;
        }
    }
    while (__builtin_expect(oDocId < docId, true)) {
        if (_decode_normal_features) {
            UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
            _decodeContext->skipFeatures(1);
            UC64_DECODECONTEXT_LOAD(o, _decodeContext->_);
            if (__builtin_expect(--_residue == 0, false)) {
                goto atbreak;
            }
        }
        UC64_DECODEEXPGOLOMB_NS(o, _doc_id_k_param.get_doc_id_k(), EC);
        oDocId += 1 + static_cast<uint32_t>(val64);
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
        if (_decode_interleaved_features) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FIELD_LENGTH, EC);
            _field_length = static_cast<uint32_t>(val64) + 1;
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUM_OCCS, EC);
            _num_occs = static_cast<uint32_t>(val64) + 1;
        }
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
ZcRareWordPostingIteratorBase<bigEndian>::doUnpack(uint32_t docId)
{
    if (!_matchData.valid()) {
        return;
    }
    if (getUnpacked()) {
        _matchData[0]->clear_hidden_from_ranking();
        return;
    }
    assert(docId == getDocId());
    if (_decode_normal_features) {
        if (_unpack_normal_features) {
            _decodeContext->unpackFeatures(_matchData, docId);
        } else {
            _decodeContext->skipFeatures(1);
            _matchData[0]->reset(docId);
            _matchData[0]->clear_hidden_from_ranking();
        }
    } else {
        _matchData[0]->reset(docId);
        _matchData[0]->clear_hidden_from_ranking();
    }
    if (_decode_interleaved_features && _unpack_interleaved_features) {
        TermFieldMatchData *tfmd = _matchData[0];
        tfmd->setFieldLength(_field_length);
        tfmd->setNumOccs(_num_occs);
    }
    setUnpacked();
}

template <bool bigEndian>
void ZcRareWordPostingIteratorBase<bigEndian>::rewind(Position start)
{
    _decodeContext->setPosition(start);
}

template <bool bigEndian, bool dynamic_k>
void
ZcRareWordPostingIterator<bigEndian, dynamic_k>::readWordStart(uint32_t docIdLimit)
{
    (void) docIdLimit;
    using EC = FeatureEncodeContext<bigEndian>;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    uint32_t length;
    uint64_t val64;

    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);

    _numDocs = static_cast<uint32_t>(val64) + 1;
    _doc_id_k_param.setup(_numDocs, docIdLimit);
    UC64_DECODEEXPGOLOMB_NS(o, _doc_id_k_param.get_doc_id_k(), EC);
    uint32_t docId = static_cast<uint32_t>(val64) + 1;
    if (_decode_interleaved_features) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FIELD_LENGTH, EC);
        _field_length = static_cast<uint32_t>(val64) + 1;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUM_OCCS, EC);
        _num_occs = static_cast<uint32_t>(val64) + 1;
    }
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);

    setDocId(docId);
    _residue = _numDocs;
    clearUnpacked();
}

ZcPostingIteratorBase::ZcPostingIteratorBase(TermFieldMatchDataArray matchData, Position start, uint32_t docIdLimit,
                                             bool decode_normal_features, bool decode_interleaved_features,
                                             bool unpack_normal_features, bool unpack_interleaved_features)
    : ZcIteratorBase(std::move(matchData), start, docIdLimit),
      _zc_decoder(),
      _zc_decoder_start(nullptr),
      _featureSeekPos(0),
      _l1(),
      _l2(),
      _l3(),
      _l4(),
      _chunk(),
      _featuresSize(0),
      _hasMore(false),
      _decode_normal_features(decode_normal_features),
      _decode_interleaved_features(decode_interleaved_features),
      _unpack_normal_features(unpack_normal_features),
      _unpack_interleaved_features(unpack_interleaved_features),
      _chunkNo(0),
      _field_length(0),
      _num_occs(0)
{
}

template <bool bigEndian>
ZcPostingIterator<bigEndian>::
ZcPostingIterator(uint32_t minChunkDocs,
                  bool dynamicK,
                  const PostingListCounts &counts,
                  search::fef::TermFieldMatchDataArray matchData,
                  Position start, uint32_t docIdLimit,
                  bool decode_normal_features, bool decode_interleaved_features,
                  bool unpack_normal_features, bool unpack_interleaved_features)
    : ZcPostingIteratorBase(std::move(matchData), start, docIdLimit,
                            decode_normal_features, decode_interleaved_features,
                            unpack_normal_features, unpack_interleaved_features),
      _decodeContext(nullptr),
      _minChunkDocs(minChunkDocs),
      _docIdK(0),
      _dynamicK(dynamicK),
      _numDocs(0),
      _featuresValI(nullptr),
      _featuresBitOffset(0),
      _counts(counts)
{ }


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::readWordStart(uint32_t docIdLimit)
{
    using EC = FeatureEncodeContext<bigEndian>;
    DecodeContextBase &d = *_decodeContext;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;

    uint32_t prevDocId = _hasMore ? _chunk._lastDocId : 0u;
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);

    _numDocs = static_cast<uint32_t>(val64) + 1;
    bool hasMore = false;
    bool features_size_flush = false;
    if (_numDocs == features_size_flush_marker) {
        features_size_flush = true;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
        _numDocs = static_cast<uint32_t>(val64) + 1;
    }
    if (__builtin_expect(_numDocs >= _minChunkDocs || features_size_flush, false)) {
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
    if (_dynamicK) {
        _docIdK = EC::calcDocIdK((_hasMore || hasMore) ? 1 : _numDocs, docIdLimit);
    }
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
    if (_decode_normal_features) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FEATURESSIZE, EC);
        _featuresSize = val64;
    }
    if (_dynamicK) {
        UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
    } else {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_LASTDOCID, EC);
    }
    _chunk._lastDocId = docIdLimit - 1 - val64;
    if (_hasMore || hasMore) {
        if (!_counts._segments.empty()) {
            assert(_chunk._lastDocId == _counts._segments[_chunkNo]._lastDoc);
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
    _zc_decoder_start = bcompr;
    _zc_decoder.set_cur(bcompr);
    bcompr += docIdsSize;
    _l1.setup(prevDocId, _chunk._lastDocId, bcompr, l1SkipSize);
    _l2.setup(prevDocId, _chunk._lastDocId, bcompr, l2SkipSize);
    _l3.setup(prevDocId, _chunk._lastDocId, bcompr, l3SkipSize);
    _l4.setup(prevDocId, _chunk._lastDocId, bcompr, l4SkipSize);
    _l1.postSetup(*this);
    _l2.postSetup(_l1);
    _l3.postSetup(_l2);
    _l4.postSetup(_l3);
    d.setByteCompr(bcompr);
    _hasMore = hasMore;
    // Save information about start of next chunk
    _featuresValI = d.getCompr();
    _featuresBitOffset = d.getBitOffset();
    _featureSeekPos = 0;
    clearUnpacked();
    // Unpack first docid delta in chunk
    nextDocId(prevDocId);
#if DEBUG_ZCPOSTING_PRINTF
    printf("Decode docId=%d\n", getDocId());
#endif
}


void
ZcPostingIteratorBase::doChunkSkipSeek(uint32_t docId)
{
    while (docId > _chunk._lastDocId && _hasMore) {
        // Skip to start of next chunk
        _featureSeekPos = 0;
        featureSeek(_featuresSize);
        _chunkNo++;
        readWordStart(getDocIdLimit()); // Read word start for next chunk
    }
    if (docId > _chunk._lastDocId) {
        _l1._skipDocId = search::endDocId;
        _l2._skipDocId = search::endDocId;
        _l3._skipDocId = search::endDocId;
        _l4._skipDocId = search::endDocId;
        setAtEnd();
    }
}


void
ZcPostingIteratorBase::doL4SkipSeek(uint32_t docId)
{
    uint32_t lastL4SkipDocId;

    if (__builtin_expect(docId > _chunk._lastDocId, false)) {
        doChunkSkipSeek(docId);
        if (docId <= _l4._skipDocId) {
            return;
        }
    }
    do {
        lastL4SkipDocId = _l4._skipDocId;
        _l4.decodeSkipEntry(_decode_normal_features);
        _l4.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
        printf("L4Decode docId %d, docIdPos %d,"
               "l1SkipPos %d, l2SkipPos %d, l3SkipPos %d, nextDocId %d\n",
               lastL4SkipDocId,
               (int) (_l4._docIdPos - _valIBase),
               (int) (_l4._l1Pos - _l1._valIBase),
               (int) (_l4._l2Pos - _l2._valIBase),
               (int) (_l4._l3Pos - _l3._valIBase),
               _l4._skipDocId);
#endif
    } while (docId > _l4._skipDocId);
    _l3._docIdPos = _l4._docIdPos;
    _l2._docIdPos = _l4._docIdPos;
    _l1._docIdPos = _l4._docIdPos;
    _zc_decoder.set_cur(_l4._docIdPos);
    _l3._skipFeaturePos = _l4._skipFeaturePos;
    _l2._skipFeaturePos = _l4._skipFeaturePos;
    _l1._skipFeaturePos = _l4._skipFeaturePos;
    _l3._skipDocId = lastL4SkipDocId;
    _l2._skipDocId = lastL4SkipDocId;
    _l1._skipDocId = lastL4SkipDocId;
    _l3._l1Pos = _l4._l1Pos;
    _l2._l1Pos = _l4._l1Pos;
    _l1._zc_decoder.set_cur(_l4._l1Pos);
    _l3._l2Pos = _l4._l2Pos;
    _l2._zc_decoder.set_cur(_l4._l2Pos);
    _l3._zc_decoder.set_cur(_l4._l3Pos);
    nextDocId(lastL4SkipDocId);
    _l1.nextDocId();
    _l2.nextDocId();
    _l3.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
    printf("L4Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d L3SkipPos %d, nextDocId %d\n",
           lastL4SkipDocId,
           (int) (_l4._docIdPos - _valIBase),
           (int) (_l4._l1Pos - _l1._valIBase),
           (int) (_l4._l2Pos - _l2._valIBase),
           (int) (_l4._l3Pos - _l3._valIBase),
           _l4._skipDocId);
#endif
    _featureSeekPos = _l4._skipFeaturePos;
    clearUnpacked();
}


void
ZcPostingIteratorBase::doL3SkipSeek(uint32_t docId)
{
    uint32_t lastL3SkipDocId;

    if (__builtin_expect(docId > _l4._skipDocId, false)) {
        doL4SkipSeek(docId);
        if (docId <= _l3._skipDocId) {
            return;
        }
    }
    do {
        lastL3SkipDocId = _l3._skipDocId;
        _l3.decodeSkipEntry(_decode_normal_features);
        _l3.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
        printf("L3Decode docId %d, docIdPos %d,"
               "l1SkipPos %d, l2SkipPos %d, nextDocId %d\n",
               lastL3SkipDocId,
               (int) (_l3._docIdPos - _valIBase),
               (int) (_l3._l1Pos - _l1._valIBase),
               (int) (_l3._l2Pos - _l2._valIBase),
               _l3._skipDocId);
#endif
    } while (docId > _l3._skipDocId);
    _l2._docIdPos = _l3._docIdPos;
    _l1._docIdPos = _l3._docIdPos;
    _zc_decoder.set_cur(_l3._docIdPos);
    _l2._skipFeaturePos = _l3._skipFeaturePos;
    _l1._skipFeaturePos = _l3._skipFeaturePos;
    _l2._skipDocId = lastL3SkipDocId;
    _l1._skipDocId = lastL3SkipDocId;
    _l2._l1Pos = _l3._l1Pos;
    _l1._zc_decoder.set_cur(_l3._l1Pos);
    _l2._zc_decoder.set_cur(_l3._l2Pos);
    nextDocId(lastL3SkipDocId);
    _l1.nextDocId();
    _l2.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
    printf("L3Seek, docId %d docIdPos %d"
           " L1SkipPos %d L2SkipPos %d, nextDocId %d\n",
           lastL3SkipDocId,
           (int) (_l3._docIdPos - _valIBase),
           (int) (_l3._l1Pos - _l1._valIBase),
           (int) (_l3._l2Pos - _l2._valIBase),
           _l3._skipDocId);
#endif
    _featureSeekPos = _l3._skipFeaturePos;
    clearUnpacked();
}


void
ZcPostingIteratorBase::doL2SkipSeek(uint32_t docId)
{
    uint32_t lastL2SkipDocId;

    if (__builtin_expect(docId > _l3._skipDocId, false)) {
        doL3SkipSeek(docId);
        if (docId <= _l2._skipDocId) {
            return;
        }
    }
    do {
        lastL2SkipDocId = _l2._skipDocId;
        _l2.decodeSkipEntry(_decode_normal_features);
        _l2.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
        printf("L2Decode docId %d, docIdPos %d, l1SkipPos %d, nextDocId %d\n",
               lastL2SkipDocId,
               (int) (_l2._docIdPos - _valIBase),
               (int) (_l2._l1Pos - _l1._valIBase),
               _l2._skipDocId);
#endif
    } while (docId > _l2._skipDocId);
    _l1._docIdPos = _l2._docIdPos;
    _zc_decoder.set_cur(_l2._docIdPos);
    _l1._skipFeaturePos = _l2._skipFeaturePos;
    _l1._skipDocId = lastL2SkipDocId;
    _l1._zc_decoder.set_cur(_l2._l1Pos);
    nextDocId(lastL2SkipDocId);
    _l1.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
    printf("L2Seek, docId %d docIdPos %d L1SkipPos %d, nextDocId %d\n",
           lastL2SkipDocId,
           (int) (_l2._docIdPos - _valIBase),
           (int) (_l2._l1Pos - _l1._valIBase),
           _l2._skipDocId);
#endif
    _featureSeekPos = _l2._skipFeaturePos;
    clearUnpacked();
}


void
ZcPostingIteratorBase::doL1SkipSeek(uint32_t docId)
{
    uint32_t lastL1SkipDocId;
    if (__builtin_expect(docId > _l2._skipDocId, false)) {
        doL2SkipSeek(docId);
        if (docId <= _l1._skipDocId) {
            return;
        }
    }
    do {
        lastL1SkipDocId = _l1._skipDocId;
        _l1.decodeSkipEntry(_decode_normal_features);
        _l1.nextDocId();
#if DEBUG_ZCPOSTING_PRINTF
        printf("L1Decode docId %d, docIdPos %d, L1SkipPos %d, nextDocId %d\n",
               lastL1SkipDocId,
               (int) (_l1._docIdPos - _valIBase),
               (int) (_l1._valI - _l1._valIBase),
                _l1._skipDocId);
#endif
    } while (docId > _l1._skipDocId);
    _zc_decoder.set_cur(_l1._docIdPos);
    nextDocId(lastL1SkipDocId);
#if DEBUG_ZCPOSTING_PRINTF
    printf("L1SkipSeek, docId %d docIdPos %d, nextDocId %d\n",
           lastL1SkipDocId,
           (int) (_l1._docIdPos - _valIBase),
           _l1._skipDocId);
#endif
    _featureSeekPos = _l1._skipFeaturePos;
    clearUnpacked();
}


void
ZcPostingIteratorBase::doSeek(uint32_t docId)
{
    if (docId > _l1._skipDocId) {
        doL1SkipSeek(docId);
    }
    uint32_t oDocId = getDocId();
#if DEBUG_ZCPOSTING_ASSERT
    assert(oDocId <= _l1._skipDocId);
    assert(docId <= _l1._skipDocId);
    assert(oDocId <= _l2._skipDocId);
    assert(docId <= _l2._skipDocId);
    assert(oDocId <= _l3._skipDocId);
    assert(docId <= _l3._skipDocId);
    assert(oDocId <= _l4._skipDocId);
    assert(docId <= _l4._skipDocId);
#endif
    ZcDecoder zc_decoder(_zc_decoder);
    uint32_t field_length = _field_length;
    uint32_t num_occs = _num_occs;
    while (__builtin_expect(oDocId < docId, true)) {
#if DEBUG_ZCPOSTING_ASSERT
        assert(oDocId <= _l1._skipDocId);
        assert(oDocId <= _l2._skipDocId);
        assert(oDocId <= _l3._skipDocId);
        assert(oDocId <= _l4._skipDocId);
#endif
        oDocId += (1 + zc_decoder.decode32());
#if DEBUG_ZCPOSTING_PRINTF
        printf("Decode docId=%d\n",
               oDocId);
#endif
        if (_decode_interleaved_features) {
            field_length = 1 + zc_decoder.decode32();
            num_occs = 1 + zc_decoder.decode32();
        }
        incNeedUnpack();
    }
    _zc_decoder = zc_decoder;
    setDocId(oDocId);
    if (_decode_interleaved_features) {
        _field_length = field_length;
        _num_occs = num_occs;
    }
    return;
}


template <bool bigEndian>
void
ZcPostingIterator<bigEndian>::doUnpack(uint32_t docId)
{
    if (!_matchData.valid()) {
        return;
    }
    if (getUnpacked()) {
        _matchData[0]->clear_hidden_from_ranking();
        return;
    }
    assert(docId == getDocId());
    if (_decode_normal_features && _unpack_normal_features) {
        if (_featureSeekPos != 0) {
            // Handle deferred feature position seek now.
            featureSeek(_featureSeekPos);
            _featureSeekPos = 0;
        }
        uint32_t needUnpack = getNeedUnpack();
        if (needUnpack > 1) {
            _decodeContext->skipFeatures(needUnpack - 1);
        }
        _decodeContext->unpackFeatures(_matchData, docId);
    } else {
        _matchData[0]->reset(docId);
        _matchData[0]->clear_hidden_from_ranking();
    }
    if (_decode_interleaved_features && _unpack_interleaved_features) {
        TermFieldMatchData *tfmd = _matchData[0];
        tfmd->setFieldLength(_field_length);
        tfmd->setNumOccs(_num_occs);
    }
    setUnpacked();
}

template <bool bigEndian>
void ZcPostingIterator<bigEndian>::rewind(Position start)
{
    _decodeContext->setPosition(start);
    _hasMore = false;
    _chunk._lastDocId = 0;
    _chunkNo = 0;
}

template class ZcRareWordPostingIterator<false, false>;
template class ZcRareWordPostingIterator<false, true>;
template class ZcRareWordPostingIterator<true, false>;
template class ZcRareWordPostingIterator<true, true>;

template class ZcPostingIterator<true>;
template class ZcPostingIterator<false>;

}
