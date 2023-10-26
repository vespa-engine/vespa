// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fakeword.h"
#include "fakeposting.h"

namespace search {

namespace fakedata {

/*
 * YST style compression of docid list.
 */
class FakeZcbFilterOcc : public FakePosting
{
private:
    std::vector<uint8_t> _compressed;
    unsigned int _docIdLimit;
    unsigned int _hitDocs;
public:
    FakeZcbFilterOcc(const FakeWord &fw);
    ~FakeZcbFilterOcc();

    static void forceLink();

    size_t bitSize() const override;
    bool hasWordPositions() const override;
    int lowLevelSinglePostingScan() const override;
    int lowLevelSinglePostingScanUnpack() const override;
    int lowLevelAndPairPostingScan(const FakePosting &rhs) const override;
    int lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const override;
    std::unique_ptr<queryeval::SearchIterator> createIterator(const fef::TermFieldMatchDataArray &matchData) const override;
};

} // namespace fakedata

} // namespace search
