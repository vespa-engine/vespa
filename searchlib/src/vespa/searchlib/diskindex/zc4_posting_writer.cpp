// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_writer.h"
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistcounts.h>

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

    uint32_t docIdsSize = _zcDocIds.size();
    uint32_t l1SkipSize = _l1Skip.size();
    uint32_t l2SkipSize = _l2Skip.size();
    uint32_t l3SkipSize = _l3Skip.size();
    uint32_t l4SkipSize = _l4Skip.size();

    e.encodeExpGolomb(docIdsSize - 1, K_VALUE_ZCPOSTING_DOCIDSSIZE);
    e.encodeExpGolomb(l1SkipSize, K_VALUE_ZCPOSTING_L1SKIPSIZE);
    if (l1SkipSize != 0) {
        e.encodeExpGolomb(l2SkipSize, K_VALUE_ZCPOSTING_L2SKIPSIZE);
        if (l2SkipSize != 0) {
            e.encodeExpGolomb(l3SkipSize, K_VALUE_ZCPOSTING_L3SKIPSIZE);
            if (l3SkipSize != 0) {
                e.encodeExpGolomb(l4SkipSize, K_VALUE_ZCPOSTING_L4SKIPSIZE);
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

    uint8_t *docIds = _zcDocIds._mallocStart;
    e.writeBits(reinterpret_cast<const uint64_t *>(docIds),
                0,
                docIdsSize * 8);
    if (l1SkipSize > 0) {
        uint8_t *l1Skip = _l1Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l1Skip),
                    0,
                    l1SkipSize * 8);
    }
    if (l2SkipSize > 0) {
        uint8_t *l2Skip = _l2Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l2Skip),
                    0,
                    l2SkipSize * 8);
    }
    if (l3SkipSize > 0) {
        uint8_t *l3Skip = _l3Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l3Skip),
                    0,
                    l3SkipSize * 8);
    }
    if (l4SkipSize > 0) {
        uint8_t *l4Skip = _l4Skip._mallocStart;
        e.writeBits(reinterpret_cast<const uint64_t *>(l4Skip),
                    0,
                    l4SkipSize * 8);
    }

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

    std::vector<DocIdAndFeatureSize>::const_iterator dit = _docIds.begin();
    std::vector<DocIdAndFeatureSize>::const_iterator dite = _docIds.end();

    for (; dit != dite; ++dit) {
        uint32_t docId = dit->_doc_id;
        uint32_t featureSize = dit->_features_size;
        e.encodeExpGolomb(docId - baseDocId, docIdK);
        baseDocId = docId + 1;
        if (_encode_interleaved_features) {
            assert(dit->_field_length > 0);
            e.encodeExpGolomb(dit->_field_length - 1, K_VALUE_ZCPOSTING_FIELD_LENGTH);
            assert(dit->_num_occs > 0);
            e.encodeExpGolomb(dit->_num_occs - 1, K_VALUE_ZCPOSTING_NUM_OCCS);
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
    // Write some pad bits to avoid decompression readahead going past
    // memory mapped file during search and into SIGSEGV territory.

    // First pad to 64 bits alignment.
    _encode_context.smallAlign(64);
    _encode_context.writeComprBufferIfNeeded();

    // Then write 128 more bits.  This allows for 64-bit decoding
    // with a readbits that always leaves a nonzero preRead
    _encode_context.padBits(128);
    _encode_context.alignDirectIO();
    _encode_context.flush();
    _encode_context.writeComprBuffer();   // Also flushes slack
}

template class Zc4PostingWriter<false>;
template class Zc4PostingWriter<true>;

}
