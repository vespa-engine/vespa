// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_writer.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistcounts.h>
#include <cassert>

using search::index::DocIdAndFeatures;
using search::index::PostingListCounts;
using search::index::PostingListParams;

namespace search::diskindex {

template <bool bigEndian>
Zc4PostingWriter<bigEndian>::Zc4PostingWriter(PostingListCounts &counts)
    : Zc4PostingWriterBase(counts),
      _encode_context(),
      _encode_features(nullptr)
{
    _encode_context.setWriteContext(&_writeContext);
    _writeContext.setEncodeContext(&_encode_context);
}

template <bool bigEndian>
Zc4PostingWriter<bigEndian>::~Zc4PostingWriter() = default;

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::write_zc_view(std::span<const uint8_t> view)
{
    if (!view.empty()) {
        _encode_context.writeBits(reinterpret_cast<const uint64_t *>(view.data()),
                                  0,
                                  view.size() * 8);
    }

}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::reset_chunk()
{
    _docIds.clear();
    if (_encode_features != nullptr) {
        _encode_features->setupWrite(_featureWriteContext);
        _featureOffset = 0;
    }
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::flush_word_with_skip(bool hasMore)
{
    assert(_docIds.size() >= _minSkipDocs || !_counts._segments.empty());

    if (_encode_features != nullptr) {
        _encode_features->flush();
    }
    EncodeContext &e = _encode_context;

    uint32_t numDocs = _docIds.size();

    e.encodeExpGolomb(numDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);
    if (numDocs >= _minChunkDocs) {
        e.writeBits((hasMore ? 1 : 0), 1);
    }

    calc_skip_info(_encode_features != nullptr);

    auto docids_view = _zcDocIds.view();
    auto l1_skip_view = _l1Skip.view();
    auto l2_skip_view = _l2Skip.view();
    auto l3_skip_view = _l3Skip.view();
    auto l4_skip_view = _l4Skip.view();

    e.encodeExpGolomb(docids_view.size() - 1, K_VALUE_ZCPOSTING_DOCIDSSIZE);
    e.encodeExpGolomb(l1_skip_view.size(), K_VALUE_ZCPOSTING_L1SKIPSIZE);
    if (!l1_skip_view.empty()) {
        e.encodeExpGolomb(l2_skip_view.size(), K_VALUE_ZCPOSTING_L2SKIPSIZE);
        if (!l2_skip_view.empty()) {
            e.encodeExpGolomb(l3_skip_view.size(), K_VALUE_ZCPOSTING_L3SKIPSIZE);
            if (!l3_skip_view.empty()) {
                e.encodeExpGolomb(l4_skip_view.size(), K_VALUE_ZCPOSTING_L4SKIPSIZE);
            }
        }
    }
    if (_encode_features != nullptr) {
        e.encodeExpGolomb(_featureOffset, K_VALUE_ZCPOSTING_FEATURESSIZE);
    }

    // Encode last document id in chunk or word.
    if (_dynamicK) {
        uint32_t docIdK = e.calcDocIdK((_counts._segments.empty() &&
                                        !hasMore) ?
                                       numDocs : 1,
                                       _docIdLimit);
        e.encodeExpGolomb(_docIdLimit - 1 - _docIds.back()._doc_id,
                          docIdK);
    } else {
        e.encodeExpGolomb(_docIdLimit - 1 - _docIds.back()._doc_id,
                          K_VALUE_ZCPOSTING_LASTDOCID);
    }

    e.smallAlign(8);    // Byte align

    write_zc_view(docids_view);
    write_zc_view(l1_skip_view);
    write_zc_view(l2_skip_view);
    write_zc_view(l3_skip_view);
    write_zc_view(l4_skip_view);

    // Write features
    e.writeBits(_featureWriteContext.getComprBuf(), 0, _featureOffset);

    _counts._numDocs += numDocs;
    if (hasMore || !_counts._segments.empty()) {
        uint64_t writePos = e.getWriteOffset();
        PostingListCounts::Segment seg;
        seg._bitLength = writePos - (_writePos + _counts._bitLength);
        seg._numDocs = numDocs;
        seg._lastDoc = _docIds.back()._doc_id;
        _counts._segments.push_back(seg);
        _counts._bitLength += seg._bitLength;
    }
    // reset tables in preparation for next word or next chunk
    clear_skip_info();
    reset_chunk();
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::write_docid_and_features(const DocIdAndFeatures &features)
{
    if (__builtin_expect(_docIds.size() >= _minChunkDocs, false)) {
        flush_word_with_skip(true);
    }
    if (_encode_features != nullptr) {
        _encode_features->writeFeatures(features);
        uint64_t writeOffset = _encode_features->getWriteOffset();
        uint64_t featureSize = writeOffset - _featureOffset;
        assert(static_cast<uint32_t>(featureSize) == featureSize);
        _docIds.emplace_back(features.doc_id(), features.field_length(), features.num_occs(),
                             static_cast<uint32_t>(featureSize));
        _featureOffset = writeOffset;
    } else {
        _docIds.emplace_back(features.doc_id(), features.field_length(), features.num_occs(), 0);
    }
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::flush_word_no_skip()
{
    // Too few document ids for skip info.
    assert(_docIds.size() < _minSkipDocs && _counts._segments.empty());

    if (_encode_features != nullptr) {
        _encode_features->flush();
    }
    EncodeContext &e = _encode_context;
    uint32_t numDocs = _docIds.size();

    e.encodeExpGolomb(numDocs - 1, K_VALUE_ZCPOSTING_NUMDOCS);

    uint32_t docIdK = _dynamicK ? e.calcDocIdK(numDocs, _docIdLimit) : K_VALUE_ZCPOSTING_DELTA_DOCID;

    uint32_t baseDocId = 1;
    const uint64_t *features = _featureWriteContext.getComprBuf();
    uint64_t featureOffset = 0;

    for (const auto& elem : _docIds) {
        uint32_t docId = elem._doc_id;
        uint32_t featureSize = elem._features_size;
        e.encodeExpGolomb(docId - baseDocId, docIdK);
        baseDocId = docId + 1;
        if (_encode_interleaved_features) {
            assert(elem._field_length > 0);
            e.encodeExpGolomb(elem._field_length - 1, K_VALUE_ZCPOSTING_FIELD_LENGTH);
            assert(elem._num_occs > 0);
            e.encodeExpGolomb(elem._num_occs - 1, K_VALUE_ZCPOSTING_NUM_OCCS);
        }
        if (featureSize != 0) {
            e.writeBits(features + (featureOffset >> 6),
                        featureOffset & 63,
                        featureSize);
            featureOffset += featureSize;
        }
    }
    _counts._numDocs += numDocs;
    reset_chunk();
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::flush_word()
{
    if (__builtin_expect(_docIds.size() >= _minSkipDocs ||
                         !_counts._segments.empty(), false)) {
        // Use skip information if enough documents or chunking has happened
        flush_word_with_skip(false);
        _numWords++;
    } else if (_docIds.size() > 0) {
        flush_word_no_skip();
        _numWords++;
    }

    EncodeContext &e = _encode_context;
    uint64_t writePos = e.getWriteOffset();

    _counts._bitLength = writePos - _writePos;
    _writePos = writePos;
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::set_encode_features(EncodeContext *encode_features)
{
    _encode_features = encode_features;
    if (_encode_features != nullptr) {
        _encode_features->setWriteContext(&_featureWriteContext);
        _encode_features->setupWrite(_featureWriteContext);
    }
    _featureWriteContext.setEncodeContext(_encode_features);
    _featureOffset = 0;
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::on_open()
{
    _numWords = 0;
    _writePos = _encode_context.getWriteOffset(); // Position after file header 
}

template <bool bigEndian>
void
Zc4PostingWriter<bigEndian>::on_close()
{
    _encode_context.pad_for_memory_map_and_flush();
}

template class Zc4PostingWriter<false>;
template class Zc4PostingWriter<true>;

}
