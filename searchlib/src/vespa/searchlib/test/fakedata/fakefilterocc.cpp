// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakefilterocc.h"
#include "fpfactory.h"
#include <vespa/searchlib/queryeval/iterators.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

namespace search::fakedata {

static FPFactoryInit
init(std::make_pair("FilterOcc",
                    makeFPFactory<FPFactoryT<FakeFilterOcc> >));

FakeFilterOcc::FakeFilterOcc(const FakeWord &fw)
    : FakePosting(fw.getName() + ".filterocc"),
      _uncompressed(),
      _docIdLimit(0),
      _hitDocs(0)
{
    std::vector<uint32_t> fake;

    for (const auto& elem : fw._postings) {
        fake.push_back(elem._docId);
    }
    std::swap(_uncompressed, fake);
    _docIdLimit = fw._docIdLimit;
    _hitDocs = fw._postings.size();
}

FakeFilterOcc::~FakeFilterOcc()
{
}

void
FakeFilterOcc::forceLink()
{
}


size_t
FakeFilterOcc::bitSize() const
{
    return 32 * _uncompressed.size();
}


bool
FakeFilterOcc::hasWordPositions() const
{
    return false;
}


int
FakeFilterOcc::lowLevelSinglePostingScan() const
{
    return 0;
}


int
FakeFilterOcc::lowLevelSinglePostingScanUnpack() const
{
    return 0;
}


int
FakeFilterOcc::
lowLevelAndPairPostingScan(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


int
FakeFilterOcc::
lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


class FakeFilterOccArrayIterator: public queryeval::RankedSearchIteratorBase
{
private:
    FakeFilterOccArrayIterator(const FakeFilterOccArrayIterator &other);

    FakeFilterOccArrayIterator& operator=(const FakeFilterOccArrayIterator &);

public:
    const uint32_t *_arr;
    const uint32_t *_arrEnd;

    FakeFilterOccArrayIterator(const uint32_t *arr,
                               const uint32_t *arrEnd,
                               const fef::TermFieldMatchDataArray &matchData);

    ~FakeFilterOccArrayIterator() override;

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


void
FakeFilterOccArrayIterator::doSeek(uint32_t docId)
{
    const uint32_t *oarr = _arr;
    const uint32_t *oarrEnd = _arrEnd;

    if (getUnpacked())
        clearUnpacked();
    if (oarr >= oarrEnd)
        goto doneuncompressed;
    for (;;) {
        if ((int) *oarr >= (int) docId)
            goto found;
        if (++oarr >= oarrEnd)
            goto doneuncompressed;
    }
 found:
    _arr = oarr;
    setDocId(*oarr);
    return;            // Still data
 doneuncompressed:
    _arr = oarr;
    setAtEnd();       // Mark end of data
    return;            // Ran off end
}


FakeFilterOccArrayIterator::
FakeFilterOccArrayIterator(const uint32_t *arr,
                           const uint32_t *arrEnd,
                           const fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _arr(arr),
      _arrEnd(arrEnd)
{
    clearUnpacked();
}

void
FakeFilterOccArrayIterator::initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);
    if (_arr < _arrEnd) {
        setDocId(*_arr);
    } else {
        setAtEnd();
    }
}

FakeFilterOccArrayIterator::~FakeFilterOccArrayIterator() = default;

void
FakeFilterOccArrayIterator::doUnpack(uint32_t docId)
{
    if (_matchData.size() != 1) {
        return;
    }
    _matchData[0]->clear_hidden_from_ranking();
    if (getUnpacked()) {
        return;
    }
    assert(docId == getDocId());
    _matchData[0]->reset(docId);
    setUnpacked();
}


std::unique_ptr<search::queryeval::SearchIterator>
FakeFilterOcc::
createIterator(const fef::TermFieldMatchDataArray &matchData) const
{
    return std::make_unique<FakeFilterOccArrayIterator>(&*_uncompressed.begin(), &*_uncompressed.end(), matchData);
}

}
