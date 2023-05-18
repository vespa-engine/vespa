// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_reader_base.h"
#include "zc4_posting_header.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <cassert>
namespace search::diskindex {

using index::PostingListCounts;
using index::DocIdAndFeatures;
using bitcompression::FeatureEncodeContext;
using bitcompression::DecodeContext64Base;

Zc4PostingReaderBase::NoSkipBase::NoSkipBase()
    : _zc_buf(),
      _doc_id(0),
      _doc_id_pos(0),
      _features_pos(0)
{
}

Zc4PostingReaderBase::NoSkipBase::~NoSkipBase() = default;

void
Zc4PostingReaderBase::NoSkipBase::setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id)
{
    _doc_id_pos = 0;
    _features_pos = 0;
    _zc_buf.clearReserve(size);
    if (size != 0) {
        decode_context.readBytes(_zc_buf._valI, size);
    }
    _zc_buf._valE = _zc_buf._valI + size;
    _doc_id = doc_id;
}

void
Zc4PostingReaderBase::NoSkipBase::check_end(uint32_t last_doc_id)
{
    assert(_doc_id == last_doc_id);
    assert(_zc_buf._valI == _zc_buf._valE);
}

Zc4PostingReaderBase::NoSkip::NoSkip()
    : NoSkipBase(),
      _field_length(1),
      _num_occs(1)
{
}

Zc4PostingReaderBase::NoSkip::~NoSkip() = default;

void
Zc4PostingReaderBase::NoSkip::read(bool decode_interleaved_features)
{
    assert(_zc_buf._valI < _zc_buf._valE);
    _doc_id += (_zc_buf.decode()+ 1);
    if (decode_interleaved_features) {
        _field_length = _zc_buf.decode() + 1;
        _num_occs = _zc_buf.decode() + 1;
    }
    _doc_id_pos = _zc_buf.pos();
}

void
Zc4PostingReaderBase::NoSkip::check_not_end(uint32_t last_doc_id)
{
    assert(_doc_id < last_doc_id);
    assert(_zc_buf._valI < _zc_buf._valE);
}

Zc4PostingReaderBase::L1Skip::L1Skip()
    : NoSkipBase(),
      _l1_skip_pos(0)
{
}

void
Zc4PostingReaderBase::L1Skip::setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id)
{
    NoSkipBase::setup(decode_context, size, doc_id);
    _l1_skip_pos = 0;
    if (size != 0) {
        next_skip_entry();
    } else {
        _doc_id = last_doc_id;
    }
}

void
Zc4PostingReaderBase::L1Skip::check(const NoSkipBase &no_skip, bool top_level, bool decode_features)
{
    assert(_doc_id == no_skip.get_doc_id());
    _doc_id_pos += (_zc_buf.decode() + 1);
    assert(_doc_id_pos == no_skip.get_doc_id_pos());
    if (decode_features) {
        _features_pos += (_zc_buf.decode() + 1);
        assert(_features_pos == no_skip.get_features_pos());
    }
    if (top_level) {
        _l1_skip_pos = _zc_buf.pos();
    }
}

void
Zc4PostingReaderBase::L1Skip::next_skip_entry()
{
    _doc_id += (_zc_buf.decode() + 1);
}

Zc4PostingReaderBase::L2Skip::L2Skip()
    : L1Skip(),
      _l2_skip_pos(0)
{
}

void
Zc4PostingReaderBase::L2Skip::setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id)
{
    L1Skip::setup(decode_context, size, doc_id, last_doc_id);
    _l2_skip_pos = 0;
}

void
Zc4PostingReaderBase::L2Skip::check(const L1Skip &l1_skip, bool top_level, bool decode_features)
{
    L1Skip::check(l1_skip, false, decode_features);
    _l1_skip_pos += (_zc_buf.decode() + 1);
    assert(_l1_skip_pos == l1_skip.get_l1_skip_pos());
    if (top_level) {
        _l2_skip_pos = _zc_buf.pos();
    }
}

Zc4PostingReaderBase::L3Skip::L3Skip()
    : L2Skip(),
      _l3_skip_pos(0)
{
}

void
Zc4PostingReaderBase::L3Skip::setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id)
{
    L2Skip::setup(decode_context, size, doc_id, last_doc_id);
    _l3_skip_pos = 0;
}

void
Zc4PostingReaderBase::L3Skip::check(const L2Skip &l2_skip, bool top_level, bool decode_features)
{
    L2Skip::check(l2_skip, false, decode_features);
    _l2_skip_pos += (_zc_buf.decode() + 1);
    assert(_l2_skip_pos == l2_skip.get_l2_skip_pos());
    if (top_level) {
        _l3_skip_pos = _zc_buf.pos();
    }
}

Zc4PostingReaderBase::L4Skip::L4Skip()
    : L3Skip()
{
}

void
Zc4PostingReaderBase::L4Skip::setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id)
{
    L3Skip::setup(decode_context, size, doc_id, last_doc_id);
}

void
Zc4PostingReaderBase::L4Skip::check(const L3Skip &l3_skip, bool decode_features)
{
    L3Skip::check(l3_skip, false, decode_features);
    _l3_skip_pos += (_zc_buf.decode() + 1);
    assert(_l3_skip_pos == l3_skip.get_l3_skip_pos());
}

Zc4PostingReaderBase::Zc4PostingReaderBase(bool dynamic_k)
    : _doc_id_k(K_VALUE_ZCPOSTING_DELTA_DOCID),
      _num_docs(0),
      _readContext(sizeof(uint64_t)),
      _has_more(false),
      _posting_params(64, 1 << 30, 10000000, dynamic_k, true, false),
      _last_doc_id(0),
      _no_skip(),
      _l1_skip(),
      _l2_skip(),
      _l3_skip(),
      _l4_skip(),
      _chunkNo(0),
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
    // Split docid & features.
    if (_no_skip.get_doc_id() >= _l1_skip.get_doc_id()) {
        _no_skip.set_features_pos(decode_context.getReadOffset());
        _l1_skip.check(_no_skip, true, _posting_params._encode_features);
        if (_no_skip.get_doc_id() >= _l2_skip.get_doc_id()) {
            _l2_skip.check(_l1_skip, true, _posting_params._encode_features);
            if (_no_skip.get_doc_id() >= _l3_skip.get_doc_id()) {
                _l3_skip.check(_l2_skip, true, _posting_params._encode_features);
                if (_no_skip.get_doc_id() >= _l4_skip.get_doc_id()) {
                    _l4_skip.check(_l3_skip, _posting_params._encode_features);
                    _l4_skip.next_skip_entry();
                }
                _l3_skip.next_skip_entry();
            }
            _l2_skip.next_skip_entry();
        }
        _l1_skip.next_skip_entry();
    }
    _no_skip.read(_posting_params._encode_interleaved_features);
    if (_residue == 1) {
        _no_skip.check_end(_last_doc_id);
        _l1_skip.check_end(_last_doc_id);
        _l2_skip.check_end(_last_doc_id);
        _l3_skip.check_end(_last_doc_id);
        _l4_skip.check_end(_last_doc_id);
    } else {
        _no_skip.check_not_end(_last_doc_id);
    }
}

void
Zc4PostingReaderBase::read_word_start_with_skip(DecodeContext64Base &decode_context, const Zc4PostingHeader &header)
{
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
    uint32_t prev_doc_id = _no_skip.get_doc_id();
    _no_skip.setup(decode_context, header._doc_ids_size, prev_doc_id);
    _l1_skip.setup(decode_context, header._l1_skip_size, prev_doc_id, _last_doc_id);
    _l2_skip.setup(decode_context, header._l2_skip_size, prev_doc_id, _last_doc_id);
    _l3_skip.setup(decode_context, header._l3_skip_size, prev_doc_id, _last_doc_id);
    _l4_skip.setup(decode_context, header._l4_skip_size, prev_doc_id, _last_doc_id);
    if (_has_more || has_more) {
        assert(_last_doc_id == _counts._segments[_chunkNo]._lastDoc);
    }
    uint64_t features_pos = decode_context.getReadOffset();
    _no_skip.set_features_pos(features_pos);
    _l1_skip.set_features_pos(features_pos);
    _l2_skip.set_features_pos(features_pos);
    _l3_skip.set_features_pos(features_pos);
    _l4_skip.set_features_pos(features_pos);
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
    if (!_has_more) {
        _no_skip.set_doc_id(0);
        _chunkNo = 0;
    } else {
        ++_chunkNo;
    }
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
