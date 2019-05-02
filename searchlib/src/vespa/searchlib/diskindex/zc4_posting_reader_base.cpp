// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_reader_base.h"
#include "zc4_posting_header.h"
#include <vespa/searchlib/index/docidandfeatures.h>

namespace search::diskindex {

using index::PostingListCounts;
using index::DocIdAndFeatures;
using bitcompression::FeatureEncodeContext;
using bitcompression::DecodeContext64Base;


Zc4PostingReaderBase::Zc4PostingReaderBase(bool dynamic_k)
    : _doc_id_k(K_VALUE_ZCPOSTING_DELTA_DOCID),
      _prev_doc_id(0),
      _num_docs(0),
      _readContext(sizeof(uint64_t)),
      _has_more(false),
      _posting_params(64, 1 << 30, 10000000, dynamic_k, true),
      _last_doc_id(0),
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
      _features_size(0),
      _counts(),
      _residue(0)
{
}

Zc4PostingReaderBase::~Zc4PostingReaderBase()
{
}

void
Zc4PostingReaderBase::read_common_word_doc_id(DecodeContext64Base &decode_context)
{
    if ((_zcDocIds._valI >= _zcDocIds._valE) && _has_more) {
        read_word_start(decode_context);    // Read start of next chunk
    }
    // Split docid & features.
    assert(_zcDocIds._valI < _zcDocIds._valE);
    uint32_t docIdPos = _zcDocIds.pos();
    uint32_t docId = _prev_doc_id + 1 + _zcDocIds.decode();
    _prev_doc_id = docId;
    assert(docId <= _last_doc_id);
    if (docId > _l1SkipDocId) {
        _l1SkipDocIdPos += _l1Skip.decode() + 1;
        assert(docIdPos == _l1SkipDocIdPos);
        uint64_t featuresPos = decode_context.getReadOffset();
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
                    assert(_l4SkipDocId <= _last_doc_id);
                    assert(_l4SkipDocId >= docId);
                }
                _l3SkipDocId += _l3Skip.decode() + 1;
                assert(_l3SkipDocId <= _last_doc_id);
                assert(_l3SkipDocId <= _l4SkipDocId);
                assert(_l3SkipDocId >= docId);
            }
            _l2SkipDocId += _l2Skip.decode() + 1;
            assert(_l2SkipDocId <= _last_doc_id);
            assert(_l2SkipDocId <= _l4SkipDocId);
            assert(_l2SkipDocId <= _l3SkipDocId);
            assert(_l2SkipDocId >= docId);
        }
        _l1SkipDocId += _l1Skip.decode() + 1;
        assert(_l1SkipDocId <= _last_doc_id);
        assert(_l1SkipDocId <= _l4SkipDocId);
        assert(_l1SkipDocId <= _l3SkipDocId);
        assert(_l1SkipDocId <= _l2SkipDocId);
        assert(_l1SkipDocId >= docId);
    }
    if (docId < _last_doc_id) {
        // Assert more space available when not yet at last docid
        assert(_zcDocIds._valI < _zcDocIds._valE);
    } else {
        // Assert that space has been used when at last docid
        assert(_zcDocIds._valI == _zcDocIds._valE);
        // Assert that we've read to end of skip info
        assert(_l1SkipDocId == _last_doc_id);
        assert(_l2SkipDocId == _last_doc_id);
        assert(_l3SkipDocId == _last_doc_id);
        assert(_l4SkipDocId == _last_doc_id);
        if (!_has_more) {
            _chunkNo = 0;
        }
    }
}

void
Zc4PostingReaderBase::read_word_start_with_skip(DecodeContext64Base &decode_context, const Zc4PostingHeader &header)
{
    if (_has_more) {
        ++_chunkNo;
    } else {
        _chunkNo = 0;
    }
    assert(_num_docs >= _posting_params._min_skip_docs || _has_more);
    bool has_more = header._has_more;
    if (_has_more || has_more) {
        assert(has_more == (_chunkNo + 1 < _counts._segments.size()));
        assert(_num_docs == _counts._segments[_chunkNo]._numDocs);
        if (has_more) {
            assert(_num_docs >= _posting_params._min_skip_docs);
            assert(_num_docs >= _posting_params._min_chunk_docs);
        }
    } else {
        assert(_num_docs >= _posting_params._min_skip_docs);
        assert(_num_docs == _counts._numDocs);
    }
    uint32_t docIdsSize = header._doc_ids_size;
    uint32_t l1SkipSize = header._l1_skip_size;
    uint32_t l2SkipSize = header._l2_skip_size;
    uint32_t l3SkipSize = header._l3_skip_size;
    uint32_t l4SkipSize = header._l4_skip_size;
    if (_has_more || has_more) {
        assert(_last_doc_id == _counts._segments[_chunkNo]._lastDoc);
    }
    _zcDocIds.clearReserve(docIdsSize);
    _l1Skip.clearReserve(l1SkipSize);
    _l2Skip.clearReserve(l2SkipSize);
    _l3Skip.clearReserve(l3SkipSize);
    _l4Skip.clearReserve(l4SkipSize);
    decode_context.readBytes(_zcDocIds._valI, docIdsSize);
    _zcDocIds._valE = _zcDocIds._valI + docIdsSize;
    if (l1SkipSize > 0) {
        decode_context.readBytes(_l1Skip._valI, l1SkipSize);
    }
    _l1Skip._valE = _l1Skip._valI + l1SkipSize;
    if (l2SkipSize > 0) {
        decode_context.readBytes(_l2Skip._valI, l2SkipSize);
    }
    _l2Skip._valE = _l2Skip._valI + l2SkipSize;
    if (l3SkipSize > 0) {
        decode_context.readBytes(_l3Skip._valI, l3SkipSize);
    }
    _l3Skip._valE = _l3Skip._valI + l3SkipSize;
    if (l4SkipSize > 0) {
        decode_context.readBytes(_l4Skip._valI, l4SkipSize);
    }
    _l4Skip._valE = _l4Skip._valI + l4SkipSize;

    if (l1SkipSize > 0) {
        _l1SkipDocId = _l1Skip.decode() + 1 + _prev_doc_id;
    } else {
        _l1SkipDocId = _last_doc_id;
    }
    if (l2SkipSize > 0) {
        _l2SkipDocId = _l2Skip.decode() + 1 + _prev_doc_id;
    } else {
        _l2SkipDocId = _last_doc_id;
    }
    if (l3SkipSize > 0) {
        _l3SkipDocId = _l3Skip.decode() + 1 + _prev_doc_id;
    } else {
        _l3SkipDocId = _last_doc_id;
    }
    if (l4SkipSize > 0) {
        _l4SkipDocId = _l4Skip.decode() + 1 + _prev_doc_id;
    } else {
        _l4SkipDocId = _last_doc_id;
    }
    _l1SkipDocIdPos = 0;
    _l1SkipFeaturesPos = decode_context.getReadOffset();
    _l2SkipDocIdPos = 0;
    _l2SkipL1SkipPos = 0;
    _l2SkipFeaturesPos = decode_context.getReadOffset();
    _l3SkipDocIdPos = 0;
    _l3SkipL1SkipPos = 0;
    _l3SkipL2SkipPos = 0;
    _l3SkipFeaturesPos = decode_context.getReadOffset();
    _l4SkipDocIdPos = 0;
    _l4SkipL1SkipPos = 0;
    _l4SkipL2SkipPos = 0;
    _l4SkipL3SkipPos = 0;
    _l4SkipFeaturesPos = decode_context.getReadOffset();
    _has_more = has_more;
    // Decode context is now positioned at start of features
}

void
Zc4PostingReaderBase::read_word_start(DecodeContext64Base &decode_context)
{
    Zc4PostingHeader header;
    header._has_more = _has_more;
    header.read(decode_context, _posting_params);
    _num_docs = header._num_docs;
    _residue = _num_docs;
    _prev_doc_id = _has_more ? _last_doc_id : 0u;
    _doc_id_k = header._doc_id_k;
    _last_doc_id = header._last_doc_id;
    _features_size = header._features_size;
    assert(_num_docs <= _counts._numDocs);
    assert(_num_docs == _counts._numDocs ||
           _num_docs >= _posting_params._min_chunk_docs ||
           _has_more);

    if (_num_docs >= _posting_params._min_skip_docs || _has_more) {
        read_word_start_with_skip(decode_context, header);
    }
}

void
Zc4PostingReaderBase::set_counts(DecodeContext64Base &decode_context, const PostingListCounts &counts)
{
    assert(!_has_more && _residue == 0);  // Previous words must have been read.
    _counts = counts;
    assert((_counts._numDocs == 0) == (_counts._bitLength == 0));
    if (_counts._numDocs > 0) {
        read_word_start(decode_context);
    }
}

}
