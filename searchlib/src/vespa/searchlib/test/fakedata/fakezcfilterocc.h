// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/diskindex/zc4_posting_params.h>

namespace search::fakedata {

/*
 * YST style compression of docid list.
 */
class FakeZcFilterOcc : public FakePosting
{
protected:
    size_t       _docIdsSize;
    size_t       _l1SkipSize;
    size_t       _l2SkipSize;
    size_t       _l3SkipSize;
    size_t       _l4SkipSize;
    unsigned int _hitDocs;
    uint32_t     _lastDocId;

    uint64_t                      _compressedBits;
    std::pair<uint64_t *, size_t> _compressed;
    vespalib::alloc::Alloc        _compressedAlloc;
    uint64_t                      _featuresSize;
    const bitcompression::PosOccFieldsParams &_fieldsParams;
    bool                          _bigEndian;
    diskindex::Zc4PostingParams   _posting_params;
protected:
    void setup(const FakeWord &fw);

    template <bool bigEndian>
    void setupT(const FakeWord &fw);

    template <bool bigEndian>
    void read_header();

    void validate_read(const FakeWord &fw) const;
    template <bool bigEndian>
    void validate_read(const FakeWord &fw) const;

public:
    explicit FakeZcFilterOcc(const FakeWord &fw);
    FakeZcFilterOcc(const FakeWord &fw,
                    bool bigEndian,
                    const diskindex::Zc4PostingParams &posting_params,
                    const char *nameSuffix);
    ~FakeZcFilterOcc() override;

    static void forceLink();

    size_t bitSize() const override;
    bool hasWordPositions() const override;
    bool has_interleaved_features() const override;
    size_t skipBitSize() const override;
    size_t l1SkipBitSize() const override;
    size_t l2SkipBitSize() const override;
    size_t l3SkipBitSize() const override;
    size_t l4SkipBitSize() const override;
    int lowLevelSinglePostingScan() const override;
    int lowLevelSinglePostingScanUnpack() const override;
    int lowLevelAndPairPostingScan(const FakePosting &rhs) const override;
    int lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const override;
    std::unique_ptr<queryeval::SearchIterator> createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

}
