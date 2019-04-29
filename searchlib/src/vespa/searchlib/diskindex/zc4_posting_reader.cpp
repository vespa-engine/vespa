// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_reader.h"
#include <vespa/searchlib/index/docidandfeatures.h>

namespace search::diskindex {

using index::PostingListCounts;
using index::DocIdAndFeatures;
using bitcompression::FeatureEncodeContext;


template <bool bigEndian>
Zc4PostingReader<bigEndian>::Zc4PostingReader(bool dynamic_k)
    : _decodeContext(nullptr),
      _docIdK(K_VALUE_ZCPOSTING_DELTA_DOCID),
      _prevDocId(0),
      _numDocs(0),
      _readContext(sizeof(uint64_t)),
      _has_more(false),
      _posting_params(64, 1 << 30, 10000000, dynamic_k, true),
      _lastDocId(0),
      _zcDocIds(),
      _l1Skip(),
      _l2Skip(),
      _l3Skip(),
      _l4Skip(),
      _chunkNo(0),
      _l1SkipDocId(0),
      _l1SkipDocIdPos(0),
      _l1SkipFeaturesPos(0),
      _l2SkipDocId(0),
      _l2SkipDocIdPos(0),
      _l2SkipL1SkipPos(0),
      _l2SkipFeaturesPos(0),
      _l3SkipDocId(0),
      _l3SkipDocIdPos(0),
      _l3SkipL1SkipPos(0),
      _l3SkipL2SkipPos(0),
      _l3SkipFeaturesPos(0),
      _l4SkipDocId(0),
      _l4SkipDocIdPos(0),
      _l4SkipL1SkipPos(0),
      _l4SkipL2SkipPos(0),
      _l4SkipL3SkipPos(0),
      _l4SkipFeaturesPos(0),
      _featuresSize(0),
      _counts(),
      _residue(0)
{
}

template <bool bigEndian>
Zc4PostingReader<bigEndian>::~Zc4PostingReader()
{
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_common_word_doc_id_and_features(DocIdAndFeatures &features)
{
    if ((_zcDocIds._valI >= _zcDocIds._valE) && _has_more) {
        read_word_start();    // Read start of next chunk
    }
    // Split docid & features.
    assert(_zcDocIds._valI < _zcDocIds._valE);
    uint32_t docIdPos = _zcDocIds.pos();
    uint32_t docId = _prevDocId + 1 + _zcDocIds.decode();
    features._docId = docId;
    _prevDocId = docId;
    assert(docId <= _lastDocId);
    if (docId > _l1SkipDocId) {
        _l1SkipDocIdPos += _l1Skip.decode() + 1;
        assert(docIdPos == _l1SkipDocIdPos);
        uint64_t featuresPos = _decodeContext->getReadOffset();
        if (_posting_params._encode_features) {
            _l1SkipFeaturesPos += _l1Skip.decode() + 1;
            assert(featuresPos == _l1SkipFeaturesPos);
        }
        (void) featuresPos;
        if (docId > _l2SkipDocId) {
            _l2SkipDocIdPos += _l2Skip.decode() + 1;
            assert(docIdPos == _l2SkipDocIdPos);
            if (_posting_params._encode_features) {
                _l2SkipFeaturesPos += _l2Skip.decode() + 1;
                assert(featuresPos == _l2SkipFeaturesPos);
            }
            _l2SkipL1SkipPos += _l2Skip.decode() + 1;
            assert(_l1Skip.pos() == _l2SkipL1SkipPos);
            if (docId > _l3SkipDocId) {
                _l3SkipDocIdPos += _l3Skip.decode() + 1;
                assert(docIdPos == _l3SkipDocIdPos);
                if (_posting_params._encode_features) {
                    _l3SkipFeaturesPos += _l3Skip.decode() + 1;
                    assert(featuresPos == _l3SkipFeaturesPos);
                }
                _l3SkipL1SkipPos += _l3Skip.decode() + 1;
                assert(_l1Skip.pos() == _l3SkipL1SkipPos);
                _l3SkipL2SkipPos += _l3Skip.decode() + 1;
                assert(_l2Skip.pos() == _l3SkipL2SkipPos);
                if (docId > _l4SkipDocId) {
                    _l4SkipDocIdPos += _l4Skip.decode() + 1;
                    assert(docIdPos == _l4SkipDocIdPos);
                    (void) docIdPos;
                    if (_posting_params._encode_features) {
                        _l4SkipFeaturesPos += _l4Skip.decode() + 1;
                        assert(featuresPos == _l4SkipFeaturesPos);
                    }
                    _l4SkipL1SkipPos += _l4Skip.decode() + 1;
                    assert(_l1Skip.pos() == _l4SkipL1SkipPos);
                    _l4SkipL2SkipPos += _l4Skip.decode() + 1;
                    assert(_l2Skip.pos() == _l4SkipL2SkipPos);
                    _l4SkipL3SkipPos += _l4Skip.decode() + 1;
                    assert(_l3Skip.pos() == _l4SkipL3SkipPos);
                    _l4SkipDocId += _l4Skip.decode() + 1;
                    assert(_l4SkipDocId <= _lastDocId);
                    assert(_l4SkipDocId >= docId);
                }
                _l3SkipDocId += _l3Skip.decode() + 1;
                assert(_l3SkipDocId <= _lastDocId);
                assert(_l3SkipDocId <= _l4SkipDocId);
                assert(_l3SkipDocId >= docId);
            }
            _l2SkipDocId += _l2Skip.decode() + 1;
            assert(_l2SkipDocId <= _lastDocId);
            assert(_l2SkipDocId <= _l4SkipDocId);
            assert(_l2SkipDocId <= _l3SkipDocId);
            assert(_l2SkipDocId >= docId);
        }
        _l1SkipDocId += _l1Skip.decode() + 1;
        assert(_l1SkipDocId <= _lastDocId);
        assert(_l1SkipDocId <= _l4SkipDocId);
        assert(_l1SkipDocId <= _l3SkipDocId);
        assert(_l1SkipDocId <= _l2SkipDocId);
        assert(_l1SkipDocId >= docId);
    }
    if (docId < _lastDocId) {
        // Assert more space available when not yet at last docid
        assert(_zcDocIds._valI < _zcDocIds._valE);
    } else {
        // Assert that space has been used when at last docid
        assert(_zcDocIds._valI == _zcDocIds._valE);
        // Assert that we've read to end of skip info
        assert(_l1SkipDocId == _lastDocId);
        assert(_l2SkipDocId == _lastDocId);
        assert(_l3SkipDocId == _lastDocId);
        assert(_l4SkipDocId == _lastDocId);
        if (!_has_more) {
            _chunkNo = 0;
        }
    }
    if (_posting_params._encode_features) {
        _decodeContext->readFeatures(features);
    }
    --_residue;
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_doc_id_and_features(DocIdAndFeatures &features)
{
    if (_residue == 0 && !_has_more) {
        if (_residue == 0) {
            // Don't read past end of posting list.
            features.clear(static_cast<uint32_t>(-1));
            return;
        }
    }
    if (_lastDocId > 0) {
        read_common_word_doc_id_and_features(features);
        return;
    }
    // Interleaves docid & features
    using EC = FeatureEncodeContext<bigEndian>;
    DecodeContext &d = *_decodeContext;
    uint32_t length;
    uint64_t val64;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);

    UC64_DECODEEXPGOLOMB_SMALL_NS(o, _docIdK, EC);
    uint32_t docId = _prevDocId + 1 + val64;
    features._docId = docId;
    _prevDocId = docId;
    UC64_DECODECONTEXT_STORE(o, d._);
    if (__builtin_expect(oCompr >= d._valE, false)) {
        _readContext.readComprBuffer();
    }
    if (_posting_params._encode_features) {
        _decodeContext->readFeatures(features);
    }
    --_residue;
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_word_start_with_skip()
{
    using EC = FeatureEncodeContext<bigEndian>;
    DecodeContext &d = *_decodeContext;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = d._valE;

    if (_has_more) {
        ++_chunkNo;
    } else {
        _chunkNo = 0;
    }
    assert(_numDocs >= _posting_params._min_skip_docs || _has_more);
    bool has_more = false;
    if (__builtin_expect(_numDocs >= _posting_params._min_chunk_docs, false)) {
        if (bigEndian) {
            has_more = static_cast<int64_t>(oVal) < 0;
            oVal <<= 1;
        } else {
            has_more = (oVal & 1) != 0;
            oVal >>= 1;
        }
        length = 1;
        UC64_READBITS_NS(o, EC);
    }
    if (_posting_params._dynamic_k) {
        _docIdK = EC::calcDocIdK((_has_more || has_more) ? 1 : _numDocs,
                                 _posting_params._doc_id_limit);
    }
    if (_has_more || has_more) {
        assert(has_more == (_chunkNo + 1 < _counts._segments.size()));
        assert(_numDocs == _counts._segments[_chunkNo]._numDocs);
        if (has_more) {
            assert(_numDocs >= _posting_params._min_skip_docs);
            assert(_numDocs >= _posting_params._min_chunk_docs);
        }
    } else {
        assert(_numDocs >= _posting_params._min_skip_docs);
        assert(_numDocs == _counts._numDocs);
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DOCIDSSIZE, EC);
    uint32_t docIdsSize = val64 + 1;
    UC64_DECODEEXPGOLOMB_NS(o,
                              K_VALUE_ZCPOSTING_L1SKIPSIZE,
                              EC);
    uint32_t l1SkipSize = val64;
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
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
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    uint32_t l4SkipSize = 0;
    if (l3SkipSize != 0) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L4SKIPSIZE, EC);
        l4SkipSize = val64;
    }
    if (_posting_params._encode_features) {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FEATURESSIZE, EC);
        _featuresSize = val64;
    }
    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    if (_posting_params._dynamic_k) {
        UC64_DECODEEXPGOLOMB_NS(o, _docIdK, EC);
    } else {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_LASTDOCID, EC);
    }
    _lastDocId = _posting_params._doc_id_limit - 1 - val64;
    if (_has_more || has_more) {
        assert(_lastDocId == _counts._segments[_chunkNo]._lastDoc);
    }

    if (__builtin_expect(oCompr >= valE, false)) {
        UC64_DECODECONTEXT_STORE(o, d._);
        _readContext.readComprBuffer();
        valE = d._valE;
        UC64_DECODECONTEXT_LOAD(o, d._);
    }
    uint64_t bytePad = oPreRead & 7;
    if (bytePad > 0) {
        length = bytePad;
        if (bigEndian) {
            oVal <<= length;
        } else {
            oVal >>= length;
        }
        UC64_READBITS_NS(o, EC);
    }
    UC64_DECODECONTEXT_STORE(o, d._);
    if (__builtin_expect(oCompr >= valE, false)) {
        _readContext.readComprBuffer();
    }
    _zcDocIds.clearReserve(docIdsSize);
    _l1Skip.clearReserve(l1SkipSize);
    _l2Skip.clearReserve(l2SkipSize);
    _l3Skip.clearReserve(l3SkipSize);
    _l4Skip.clearReserve(l4SkipSize);
    _decodeContext->readBytes(_zcDocIds._valI, docIdsSize);
    _zcDocIds._valE = _zcDocIds._valI + docIdsSize;
    if (l1SkipSize > 0) {
        _decodeContext->readBytes(_l1Skip._valI, l1SkipSize);
    }
    _l1Skip._valE = _l1Skip._valI + l1SkipSize;
    if (l2SkipSize > 0) {
        _decodeContext->readBytes(_l2Skip._valI, l2SkipSize);
    }
    _l2Skip._valE = _l2Skip._valI + l2SkipSize;
    if (l3SkipSize > 0) {
        _decodeContext->readBytes(_l3Skip._valI, l3SkipSize);
    }
    _l3Skip._valE = _l3Skip._valI + l3SkipSize;
    if (l4SkipSize > 0) {
        _decodeContext->readBytes(_l4Skip._valI, l4SkipSize);
    }
    _l4Skip._valE = _l4Skip._valI + l4SkipSize;

    if (l1SkipSize > 0) {
        _l1SkipDocId = _l1Skip.decode() + 1 + _prevDocId;
    } else {
        _l1SkipDocId = _lastDocId;
    }
    if (l2SkipSize > 0) {
        _l2SkipDocId = _l2Skip.decode() + 1 + _prevDocId;
    } else {
        _l2SkipDocId = _lastDocId;
    }
    if (l3SkipSize > 0) {
        _l3SkipDocId = _l3Skip.decode() + 1 + _prevDocId;
    } else {
        _l3SkipDocId = _lastDocId;
    }
    if (l4SkipSize > 0) {
        _l4SkipDocId = _l4Skip.decode() + 1 + _prevDocId;
    } else {
        _l4SkipDocId = _lastDocId;
    }
    _l1SkipDocIdPos = 0;
    _l1SkipFeaturesPos = _decodeContext->getReadOffset();
    _l2SkipDocIdPos = 0;
    _l2SkipL1SkipPos = 0;
    _l2SkipFeaturesPos = _decodeContext->getReadOffset();
    _l3SkipDocIdPos = 0;
    _l3SkipL1SkipPos = 0;
    _l3SkipL2SkipPos = 0;
    _l3SkipFeaturesPos = _decodeContext->getReadOffset();
    _l4SkipDocIdPos = 0;
    _l4SkipL1SkipPos = 0;
    _l4SkipL2SkipPos = 0;
    _l4SkipL3SkipPos = 0;
    _l4SkipFeaturesPos = _decodeContext->getReadOffset();
    _has_more = has_more;
    // Decode context is now positioned at start of features
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_word_start()
{
    using EC = FeatureEncodeContext<bigEndian>;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, _decodeContext->_);
    uint32_t length;
    uint64_t val64;
    const uint64_t *valE = _decodeContext->_valE;

    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    UC64_DECODECONTEXT_STORE(o, _decodeContext->_);
    if (oCompr >= valE) {
        _readContext.readComprBuffer();
    }
    _numDocs = static_cast<uint32_t>(val64) + 1;
    _residue = _numDocs;
    _prevDocId = _has_more ? _lastDocId : 0u;
    assert(_numDocs <= _counts._numDocs);
    assert(_numDocs == _counts._numDocs ||
           _numDocs >= _posting_params._min_chunk_docs ||
           _has_more);

    if (_numDocs >= _posting_params._min_skip_docs || _has_more) {
        read_word_start_with_skip();
        // Decode context is not positioned at start of features
    } else {
        if (_posting_params._dynamic_k) {
            _docIdK = EC::calcDocIdK(_numDocs, _posting_params._doc_id_limit);
        }
        _lastDocId = 0u;
        // Decode context is not positioned at start of docids & features
    }
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::set_counts(const PostingListCounts &counts)
{
    assert(!_has_more && _residue == 0);  // Previous words must have been read.
    _counts = counts;
    assert((_counts._numDocs == 0) == (_counts._bitLength == 0));
    if (_counts._numDocs > 0) {
        read_word_start();
    }
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::set_decode_features(DecodeContext *decode_features)
{
    _decodeContext = decode_features;
    _decodeContext->setReadContext(&_readContext);
    _readContext.setDecodeContext(_decodeContext);
}

template class Zc4PostingReader<false>;
template class Zc4PostingReader<true>;

}
