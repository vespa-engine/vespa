// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"

namespace search
{

namespace fakedata
{

/*
 * YST style compression of docid list.
 */
class FakeZcbFilterOcc : public FakePosting
{
private:
    std::vector<uint8_t> _compressed;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
    size_t _bitSize;
public:
    FakeZcbFilterOcc(const FakeWord &fw);

    ~FakeZcbFilterOcc(void);

    static void
    forceLink(void);

    /*
     * Size of posting list, in bits.
     */
    size_t bitSize(void) const override;

    virtual bool hasWordPositions(void) const override;

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

