// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/searchiterator.h>


namespace search::test {

/*
 * Test search iterator used by SearchIteratorVerifier and
 * InitRangeVerifier.
 */
class DocIdIterator : public search::queryeval::SearchIterator
{
    const bool                  _strict;
    uint32_t                    _currIndex;
    const std::vector<uint32_t> _docIds;

public:
    DocIdIterator(const std::vector<uint32_t>& docIds, bool strict);
    ~DocIdIterator() override;
    void initRange(uint32_t beginId, uint32_t endId) override;
    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docid) override;
    vespalib::Trinary is_strict() const override;
};

}
