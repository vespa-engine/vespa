// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_writer_base.h"
#include <vespa/searchlib/index/postinglistcounts.h>

using search::index::PostingListCounts;
using search::index::PostingListParams;

namespace search::diskindex
{

Zc4PostingWriterBase::Zc4PostingWriterBase(PostingListCounts &counts)
    : _minChunkDocs(1 << 30),
      _minSkipDocs(64),
      _docIdLimit(10000000),
      _docIds(),
      _featureOffset(0),
      _writePos(0),
      _dynamicK(false),
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

Zc4PostingWriterBase::~Zc4PostingWriterBase()
{
}

#define L1SKIPSTRIDE 16
#define L2SKIPSTRIDE 8
#define L3SKIPSTRIDE 8
#define L4SKIPSTRIDE 8

void
Zc4PostingWriterBase::calc_skip_info(bool encodeFeatures)
{
    uint32_t lastDocId = 0u;
    uint32_t lastL1SkipDocId = 0u;
    uint32_t lastL1SkipDocIdPos = 0;
    uint32_t lastL1SkipFeaturePos = 0;
    uint32_t lastL2SkipDocId = 0u;
    uint32_t lastL2SkipDocIdPos = 0;
    uint32_t lastL2SkipFeaturePos = 0;
    uint32_t lastL2SkipL1SkipPos = 0;
    uint32_t lastL3SkipDocId = 0u;
    uint32_t lastL3SkipDocIdPos = 0;
    uint32_t lastL3SkipFeaturePos = 0;
    uint32_t lastL3SkipL1SkipPos = 0;
    uint32_t lastL3SkipL2SkipPos = 0;
    uint32_t lastL4SkipDocId = 0u;
    uint32_t lastL4SkipDocIdPos = 0;
    uint32_t lastL4SkipFeaturePos = 0;
    uint32_t lastL4SkipL1SkipPos = 0;
    uint32_t lastL4SkipL2SkipPos = 0;
    uint32_t lastL4SkipL3SkipPos = 0;
    unsigned int l1SkipCnt = 0;
    unsigned int l2SkipCnt = 0;
    unsigned int l3SkipCnt = 0;
    unsigned int l4SkipCnt = 0;
    uint64_t featurePos = 0;

    std::vector<DocIdAndFeatureSize>::const_iterator dit = _docIds.begin();
    std::vector<DocIdAndFeatureSize>::const_iterator dite = _docIds.end();

    if (!_counts._segments.empty()) {
        lastDocId = _counts._segments.back()._lastDoc;
        lastL1SkipDocId = lastDocId;
        lastL2SkipDocId = lastDocId;
        lastL3SkipDocId = lastDocId;
        lastL4SkipDocId = lastDocId;
    }

    for (; dit != dite; ++dit) {
        if (l1SkipCnt >= L1SKIPSTRIDE) {
            // L1 docid delta
            uint32_t docIdDelta = lastDocId - lastL1SkipDocId;
            assert(static_cast<int32_t>(docIdDelta) > 0);
            _l1Skip.encode(docIdDelta - 1);
            lastL1SkipDocId = lastDocId;
            // L1 docid pos
            uint64_t docIdPos = _zcDocIds.size();
            _l1Skip.encode(docIdPos - lastL1SkipDocIdPos - 1);
            lastL1SkipDocIdPos = docIdPos;
            if (encodeFeatures) {
                // L1 features pos
                _l1Skip.encode(featurePos - lastL1SkipFeaturePos - 1);
                lastL1SkipFeaturePos = featurePos;
            }
            l1SkipCnt = 0;
            ++l2SkipCnt;
            if (l2SkipCnt >= L2SKIPSTRIDE) {
                // L2 docid delta
                docIdDelta = lastDocId - lastL2SkipDocId;
                assert(static_cast<int32_t>(docIdDelta) > 0);
                _l2Skip.encode(docIdDelta - 1);
                lastL2SkipDocId = lastDocId;
                // L2 docid pos
                docIdPos = _zcDocIds.size();
                _l2Skip.encode(docIdPos - lastL2SkipDocIdPos - 1);
                lastL2SkipDocIdPos = docIdPos;
                if (encodeFeatures) {
                    // L2 features pos
                    _l2Skip.encode(featurePos - lastL2SkipFeaturePos - 1);
                    lastL2SkipFeaturePos = featurePos;
                }
                // L2 L1Skip pos
                uint64_t l1SkipPos = _l1Skip.size();
                _l2Skip.encode(l1SkipPos - lastL2SkipL1SkipPos - 1);
                lastL2SkipL1SkipPos = l1SkipPos;
                l2SkipCnt = 0;
                ++l3SkipCnt;
                if (l3SkipCnt >= L3SKIPSTRIDE) {
                    // L3 docid delta
                    docIdDelta = lastDocId - lastL3SkipDocId;
                    assert(static_cast<int32_t>(docIdDelta) > 0);
                    _l3Skip.encode(docIdDelta - 1);
                    lastL3SkipDocId = lastDocId;
                    // L3 docid pos
                    docIdPos = _zcDocIds.size();
                    _l3Skip.encode(docIdPos - lastL3SkipDocIdPos - 1);
                    lastL3SkipDocIdPos = docIdPos;
                    if (encodeFeatures) {
                        // L3 features pos
                        _l3Skip.encode(featurePos - lastL3SkipFeaturePos - 1);
                        lastL3SkipFeaturePos = featurePos;
                    }
                    // L3 L1Skip pos
                    l1SkipPos = _l1Skip.size();
                    _l3Skip.encode(l1SkipPos - lastL3SkipL1SkipPos - 1);
                    lastL3SkipL1SkipPos = l1SkipPos;
                    // L3 L2Skip pos
                    uint64_t l2SkipPos = _l2Skip.size();
                    _l3Skip.encode(l2SkipPos - lastL3SkipL2SkipPos - 1);
                    lastL3SkipL2SkipPos = l2SkipPos;
                    l3SkipCnt = 0;
                    ++l4SkipCnt;
                    if (l4SkipCnt >= L4SKIPSTRIDE) {
                        // L4 docid delta
                        docIdDelta = lastDocId - lastL4SkipDocId;
                        assert(static_cast<int32_t>(docIdDelta) > 0);
                        _l4Skip.encode(docIdDelta - 1);
                        lastL4SkipDocId = lastDocId;
                        // L4 docid pos
                        docIdPos = _zcDocIds.size();
                        _l4Skip.encode(docIdPos - lastL4SkipDocIdPos - 1);
                        lastL4SkipDocIdPos = docIdPos;
                        if (encodeFeatures) {
                            // L4 features pos
                            _l4Skip.encode(featurePos - lastL4SkipFeaturePos - 1);
                            lastL4SkipFeaturePos = featurePos;
                        }
                        // L4 L1Skip pos
                        l1SkipPos = _l1Skip.size();
                        _l4Skip.encode(l1SkipPos - lastL4SkipL1SkipPos - 1);
                        lastL4SkipL1SkipPos = l1SkipPos;
                        // L4 L2Skip pos
                        l2SkipPos = _l2Skip.size();
                        _l4Skip.encode(l2SkipPos - lastL4SkipL2SkipPos - 1);
                        lastL4SkipL2SkipPos = l2SkipPos;
                        // L4 L3Skip pos
                        uint64_t l3SkipPos = _l3Skip.size();
                        _l4Skip.encode(l3SkipPos - lastL4SkipL3SkipPos - 1);
                        lastL4SkipL3SkipPos = l3SkipPos;
                        l4SkipCnt = 0;
                    }
                }
            }
        }
        uint32_t docId = dit->first;
        featurePos += dit->second;
        _zcDocIds.encode(docId - lastDocId - 1);
        lastDocId = docId;
        ++l1SkipCnt;
    }
    // Extra partial entries for skip tables to simplify iterator during search
    if (_l1Skip.size() > 0) {
        _l1Skip.encode(lastDocId - lastL1SkipDocId - 1);
    }
    if (_l2Skip.size() > 0) {
        _l2Skip.encode(lastDocId - lastL2SkipDocId - 1);
    }
    if (_l3Skip.size() > 0) {
        _l3Skip.encode(lastDocId - lastL3SkipDocId - 1);
    }
    if (_l4Skip.size() > 0) {
        _l4Skip.encode(lastDocId - lastL4SkipDocId - 1);
    }
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
}

}
