// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search {

namespace fakedata {

/*
 * YST style compression of docid list.
 */
class FakeZcFilterOcc : public FakePosting
{
protected:
    size_t _docIdsSize;
    size_t _l1SkipSize;
    size_t _l2SkipSize;
    size_t _l3SkipSize;
    size_t _l4SkipSize;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
    uint32_t _lastDocId;

    uint64_t _compressedBits;
    std::pair<uint64_t *, size_t> _compressed;
    void *_compressedMalloc;
    uint64_t _featuresSize;
    const search::bitcompression::PosOccFieldsParams &_fieldsParams;
    bool _bigEndian;
protected:
    void setup(const FakeWord &fw, bool doFeatures, bool dynamicK);

    template <bool bigEndian>
    void setupT(const FakeWord &fw, bool doFeatures, bool dynamicK);

    template <bool bigEndian>
    void read_header(bool do_features, bool dynamic_k, uint32_t min_skip_docs, uint32_t min_cunk_docs);

public:
    FakeZcFilterOcc(const FakeWord &fw);
    FakeZcFilterOcc(const FakeWord &fw, bool bigEndian, const char *nameSuffix);
    ~FakeZcFilterOcc();

    static void forceLink();

    size_t bitSize() const override;
    bool hasWordPositions() const override;
    size_t skipBitSize() const override;
    size_t l1SkipBitSize() const override;
    size_t l2SkipBitSize() const override;
    size_t l3SkipBitSize() const override;
    size_t l4SkipBitSize() const override;
    int lowLevelSinglePostingScan() const override;
    int lowLevelSinglePostingScanUnpack() const override;
    int lowLevelAndPairPostingScan(const FakePosting &rhs) const override;
    int lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const override;
    queryeval::SearchIterator *createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

} // namespace fakedata

} // namespace search
