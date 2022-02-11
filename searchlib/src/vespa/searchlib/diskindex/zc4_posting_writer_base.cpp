// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_writer_base.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/postinglistparams.h>

using search::index::PostingListCounts;
using search::index::PostingListParams;

namespace search::diskindex {

namespace {

class DocIdEncoder {
protected:
    uint32_t _doc_id;
    uint32_t _doc_id_pos;
    uint32_t _feature_pos;
    using DocIdAndFeatureSize = Zc4PostingWriterBase::DocIdAndFeatureSize;

public:
    DocIdEncoder()
        : _doc_id(0u),
          _doc_id_pos(0u),
          _feature_pos(0u)
    {
    }

    void write(ZcBuf &zc_buf, const DocIdAndFeatureSize &doc_id_and_feature_size, bool encode_interleaved_features);
    void set_doc_id(uint32_t doc_id) { _doc_id = doc_id; }
    uint32_t get_doc_id() const { return _doc_id; }
    uint32_t get_doc_id_pos() const { return _doc_id_pos; }
    uint32_t get_feature_pos() const { return _feature_pos; }
};

class L1SkipEncoder : public DocIdEncoder {
protected:
    uint32_t _stride_check;
    uint32_t _l1_skip_pos;
    const bool _encode_features;

public:
    L1SkipEncoder(bool encode_features)
        : DocIdEncoder(),
          _stride_check(0u),
          _l1_skip_pos(0u),
          _encode_features(encode_features)
    {
    }

    void encode_skip(ZcBuf &zc_buf, const DocIdEncoder &doc_id_encoder);
    void write_skip(ZcBuf &zc_buf, const DocIdEncoder &doc_id_encoder);
    bool should_write_skip(uint32_t stride) { return ++_stride_check >= stride; }
    void dec_stride_check() { --_stride_check; }
    void write_partial_skip(ZcBuf &zc_buf, uint32_t doc_id);
    uint32_t get_l1_skip_pos() const { return _l1_skip_pos; }
};

struct L2SkipEncoder : public L1SkipEncoder {
protected:
    uint32_t _l2_skip_pos;

public:
    L2SkipEncoder(bool encode_features)
        : L1SkipEncoder(encode_features),
          _l2_skip_pos(0u)
    {
    }

    void encode_skip(ZcBuf &zc_buf, const L1SkipEncoder &l1_skip);
    void write_skip(ZcBuf &zc_buf, const L1SkipEncoder &l1_skip);
    uint32_t get_l2_skip_pos() const { return _l2_skip_pos; }
};

class L3SkipEncoder : public L2SkipEncoder {
protected:
    uint32_t _l3_skip_pos;

public:
    L3SkipEncoder(bool encode_features)
        : L2SkipEncoder(encode_features),
          _l3_skip_pos(0u)
    {
    }

    void encode_skip(ZcBuf &zc_buf, const L2SkipEncoder &l2_skip);
    void write_skip(ZcBuf &zc_buf, const L2SkipEncoder &l2_skip);
    uint32_t get_l3_skip_pos() const { return _l3_skip_pos; }
};

class L4SkipEncoder : public L3SkipEncoder {

public:
    L4SkipEncoder(bool encode_features)
        : L3SkipEncoder(encode_features)
    {
    }

    void encode_skip(ZcBuf &zc_buf, const L3SkipEncoder &l3_skip);
    void write_skip(ZcBuf &zc_buf, const L3SkipEncoder &l3_skip);
};

void
DocIdEncoder::write(ZcBuf &zc_buf, const DocIdAndFeatureSize &doc_id_and_feature_size, bool encode_interleaved_features)
{
    _feature_pos += doc_id_and_feature_size._features_size;
    zc_buf.encode(doc_id_and_feature_size._doc_id - _doc_id - 1);
    _doc_id = doc_id_and_feature_size._doc_id;
    if (encode_interleaved_features) {
        assert(doc_id_and_feature_size._field_length > 0);
        zc_buf.encode(doc_id_and_feature_size._field_length - 1);
        assert(doc_id_and_feature_size._num_occs > 0);
        zc_buf.encode(doc_id_and_feature_size._num_occs - 1);
    }
    _doc_id_pos = zc_buf.size();
}

void
L1SkipEncoder::encode_skip(ZcBuf &zc_buf, const DocIdEncoder &doc_id_encoder)
{
    _stride_check = 0;
    // doc id
    uint32_t doc_id_delta = doc_id_encoder.get_doc_id() - _doc_id;
    assert(static_cast<int32_t>(doc_id_delta) > 0);
    zc_buf.encode(doc_id_delta - 1);
    _doc_id = doc_id_encoder.get_doc_id();
    // doc id pos
    zc_buf.encode(doc_id_encoder.get_doc_id_pos() - _doc_id_pos - 1);
    _doc_id_pos = doc_id_encoder.get_doc_id_pos();
    if (_encode_features) {
        // features pos
        zc_buf.encode(doc_id_encoder.get_feature_pos() - _feature_pos - 1);
        _feature_pos = doc_id_encoder.get_feature_pos();
    }
}

void
L1SkipEncoder::write_skip(ZcBuf &zc_buf, const DocIdEncoder &doc_id_encoder)
{
    encode_skip(zc_buf, doc_id_encoder);
    _l1_skip_pos = zc_buf.size();
}

void
L1SkipEncoder::write_partial_skip(ZcBuf &zc_buf, uint32_t doc_id)
{
    if (zc_buf.size() > 0) {
        zc_buf.encode(doc_id - _doc_id - 1);
    }
}

void
L2SkipEncoder::encode_skip(ZcBuf &zc_buf, const L1SkipEncoder &l1_skip)
{
    L1SkipEncoder::encode_skip(zc_buf, l1_skip);
    // L1 skip pos
    zc_buf.encode(l1_skip.get_l1_skip_pos() - _l1_skip_pos - 1);
    _l1_skip_pos = l1_skip.get_l1_skip_pos();
}

void
L2SkipEncoder::write_skip(ZcBuf &zc_buf, const L1SkipEncoder &l1_skip)
{
    encode_skip(zc_buf, l1_skip);
    _l2_skip_pos = zc_buf.size();
}

void
L3SkipEncoder::encode_skip(ZcBuf &zc_buf, const L2SkipEncoder &l2_skip)
{
    L2SkipEncoder::encode_skip(zc_buf, l2_skip);
    // L2 skip pos
    zc_buf.encode(l2_skip.get_l2_skip_pos() - _l2_skip_pos - 1);
    _l2_skip_pos = l2_skip.get_l2_skip_pos();
}

void
L3SkipEncoder::write_skip(ZcBuf &zc_buf, const L2SkipEncoder &l2_skip)
{
    encode_skip(zc_buf, l2_skip);
    _l3_skip_pos = zc_buf.size();
}

void
L4SkipEncoder::encode_skip(ZcBuf &zc_buf, const L3SkipEncoder &l3_skip)
{
    L3SkipEncoder::encode_skip(zc_buf, l3_skip);
    // L3 skip pos
    zc_buf.encode(l3_skip.get_l3_skip_pos() - _l3_skip_pos - 1);
    _l3_skip_pos = l3_skip.get_l3_skip_pos();
}

void
L4SkipEncoder::write_skip(ZcBuf &zc_buf, const L3SkipEncoder &l3_skip)
{
    encode_skip(zc_buf, l3_skip);
}

}

Zc4PostingWriterBase::Zc4PostingWriterBase(PostingListCounts &counts)
    : _minChunkDocs(1 << 30),
      _minSkipDocs(64),
      _docIdLimit(10000000),
      _docIds(),
      _featureOffset(0),
      _writePos(0),
      _dynamicK(false),
      _encode_interleaved_features(false),
      _zcDocIds(),
      _l1Skip(),
      _l2Skip(),
      _l3Skip(),
      _l4Skip(),
      _numWords(0),
      _counts(counts),
      _writeContext(sizeof(uint64_t)),
      _featureWriteContext(sizeof(uint64_t))
{
    _featureWriteContext.allocComprBuf(64, 1);
    // Ensure that some space is initially available in encoding buffers
    _zcDocIds.maybeExpand();
    _l1Skip.maybeExpand();
    _l2Skip.maybeExpand();
    _l3Skip.maybeExpand();
    _l4Skip.maybeExpand();
}

Zc4PostingWriterBase::~Zc4PostingWriterBase() = default;

#define L1SKIPSTRIDE 16
#define L2SKIPSTRIDE 8
#define L3SKIPSTRIDE 8
#define L4SKIPSTRIDE 8

void
Zc4PostingWriterBase::calc_skip_info(bool encode_features)
{
    DocIdEncoder doc_id_encoder;
    L1SkipEncoder l1_skip_encoder(encode_features);
    L2SkipEncoder l2_skip_encoder(encode_features);
    L3SkipEncoder l3_skip_encoder(encode_features);
    L4SkipEncoder l4_skip_encoder(encode_features);
    l1_skip_encoder.dec_stride_check();
    if (!_counts._segments.empty()) {
        uint32_t doc_id = _counts._segments.back()._lastDoc;
        doc_id_encoder.set_doc_id(doc_id);
        l1_skip_encoder.set_doc_id(doc_id);
        l2_skip_encoder.set_doc_id(doc_id);
        l3_skip_encoder.set_doc_id(doc_id);
        l4_skip_encoder.set_doc_id(doc_id);
    }
    for (const auto &doc_id_and_feature_size : _docIds) {
        if (l1_skip_encoder.should_write_skip(L1SKIPSTRIDE)) {
            l1_skip_encoder.write_skip(_l1Skip, doc_id_encoder);
            if (l2_skip_encoder.should_write_skip(L2SKIPSTRIDE)) {
                l2_skip_encoder.write_skip(_l2Skip, l1_skip_encoder);
                if (l3_skip_encoder.should_write_skip(L3SKIPSTRIDE)) {
                    l3_skip_encoder.write_skip(_l3Skip, l2_skip_encoder);
                    if (l4_skip_encoder.should_write_skip(L4SKIPSTRIDE)) {
                        l4_skip_encoder.write_skip(_l4Skip, l3_skip_encoder);
                    }
                }
            }
        }
        doc_id_encoder.write(_zcDocIds, doc_id_and_feature_size, _encode_interleaved_features);
    }
    // Extra partial entries for skip tables to simplify iterator during search
    l1_skip_encoder.write_partial_skip(_l1Skip, doc_id_encoder.get_doc_id());
    l2_skip_encoder.write_partial_skip(_l2Skip, doc_id_encoder.get_doc_id());
    l3_skip_encoder.write_partial_skip(_l3Skip, doc_id_encoder.get_doc_id());
    l4_skip_encoder.write_partial_skip(_l4Skip, doc_id_encoder.get_doc_id());
}

void
Zc4PostingWriterBase::clear_skip_info()
{
    _zcDocIds.clear();
    _l1Skip.clear();
    _l2Skip.clear();
    _l3Skip.clear();
    _l4Skip.clear();
}

void
Zc4PostingWriterBase::set_posting_list_params(const PostingListParams &params)
{
    params.get("docIdLimit", _docIdLimit);
    params.get("minChunkDocs", _minChunkDocs);
    params.get("minSkipDocs", _minSkipDocs);
    params.get("interleaved_features", _encode_interleaved_features);
}

}
