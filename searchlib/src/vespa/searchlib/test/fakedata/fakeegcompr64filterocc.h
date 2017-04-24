// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"

namespace search
{

namespace fakedata
{

/*
 * Old compressed posocc format.
 */
class FakeEGCompr64FilterOcc : public FakePosting
{
protected:
    std::pair<uint64_t *, size_t> _compressed;
    std::pair<uint64_t *, size_t> _l1SkipCompressed;
    std::pair<uint64_t *, size_t> _l2SkipCompressed;
    std::pair<uint64_t *, size_t> _l3SkipCompressed;
    std::pair<uint64_t *, size_t> _l4SkipCompressed;
    void *_compressedMalloc;
    void *_l1SkipCompressedMalloc;
    void *_l2SkipCompressedMalloc;
    void *_l3SkipCompressedMalloc;
    void *_l4SkipCompressedMalloc;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
    uint32_t _lastDocId;
    size_t _bitSize;
    size_t _l1SkipBitSize;
    size_t _l2SkipBitSize;
    size_t _l3SkipBitSize;
    size_t _l4SkipBitSize;
    bool _bigEndian;

private:
    void setup(const FakeWord &fw);

    template <bool bigEndian>
    void
    setupT(const FakeWord &fw);

public:
    FakeEGCompr64FilterOcc(const FakeWord &fw);

    FakeEGCompr64FilterOcc(const FakeWord &fw,
                           bool bigEndian,
                           const char *nameSuffix);

    ~FakeEGCompr64FilterOcc(void);

    static void
    forceLink(void);

    /*
     * Size of posting list, in bits.
     */
    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

    /*
     * Size of posting skip list, in bits.
     */
    size_t skipBitSize(void) const override;
    size_t l1SkipBitSize(void) const override;
    size_t l2SkipBitSize(void) const override;
    size_t l3SkipBitSize(void) const override;
    size_t l4SkipBitSize(void) const override;

    /*
     * Single posting list performance, without feature unpack.
     */
    virtual int
    lowLevelSinglePostingScan(void) const override;

    /*
     * Single posting list performance, with feature unpack.
     */
    virtual int
    lowLevelSinglePostingScanUnpack(void) const override;

    /*
     * Two posting lists performance (same format) without feature unpack.
     */
    virtual int
    lowLevelAndPairPostingScan(const FakePosting &rhs) const override;

    /*
     * Two posting lists performance (same format) with feature unpack.
     */
    virtual int
    lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const override;


    /*
     * Iterator factory, for current query evaluation framework.
     */
    virtual search::queryeval::SearchIterator *
    createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

} // namespace fakedata

} // namespace search

