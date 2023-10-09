// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"

namespace search::fakedata {

/*
 * Old compressed posocc format.
 */
class FakeEGCompr64FilterOcc : public FakePosting
{
protected:
    using Alloc = vespalib::alloc::Alloc;
    std::pair<uint64_t *, size_t> _compressed;
    std::pair<uint64_t *, size_t> _l1SkipCompressed;
    std::pair<uint64_t *, size_t> _l2SkipCompressed;
    std::pair<uint64_t *, size_t> _l3SkipCompressed;
    std::pair<uint64_t *, size_t> _l4SkipCompressed;
    Alloc _compressedAlloc;
    Alloc _l1SkipCompressedAlloc;
    Alloc _l2SkipCompressedAlloc;
    Alloc _l3SkipCompressedAlloc;
    Alloc _l4SkipCompressedAlloc;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
    uint32_t     _lastDocId;
    size_t       _bitSize;
    size_t       _l1SkipBitSize;
    size_t       _l2SkipBitSize;
    size_t       _l3SkipBitSize;
    size_t       _l4SkipBitSize;
    bool         _bigEndian;

private:
    void setup(const FakeWord &fw);

    template <bool bigEndian>
    void setupT(const FakeWord &fw);

public:
    explicit FakeEGCompr64FilterOcc(const FakeWord &fw);
    FakeEGCompr64FilterOcc(const FakeWord &fw, bool bigEndian, const char *nameSuffix);

    ~FakeEGCompr64FilterOcc() override;

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
    std::unique_ptr<queryeval::SearchIterator> createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

}
