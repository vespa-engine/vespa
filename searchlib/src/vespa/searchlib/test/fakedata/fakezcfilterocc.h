// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


#include "fakeword.h"
#include "fakeposting.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace search
{

namespace fakedata
{

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
    void
    setupT(const FakeWord &fw, bool doFeatures, bool dynamicK);

public:
    FakeZcFilterOcc(const FakeWord &fw);

    FakeZcFilterOcc(const FakeWord &fw,
                    bool bigEndian,
                    const char *nameSuffix);

    ~FakeZcFilterOcc(void);

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

