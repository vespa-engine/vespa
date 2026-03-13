// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakezcbfilterocc.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/queryeval/iterators.h>
#include "fpfactory.h"

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

namespace search::fakedata {

static FPFactoryInit
init(std::make_pair("ZcbFilterOcc",
                    makeFPFactory<FPFactoryT<FakeZcbFilterOcc> >));

static void
zcbEncode(std::vector<uint8_t> &bytes,
         uint32_t num)
{
    if (num < (1 << 7)) {
        num <<= 1;
        num += 1;
    } else if (num < (1 << 14)) {
        num <<= 2;
        num += 2;
    } else if (num < (1 << 21)) {
        num <<= 3;
        num += 4;
    } else
        num <<= 4;

    do {
        bytes.push_back(num & 0xff);
        num >>= 8;
    } while (num != 0);
}


#define ZCBDECODE(valI, resop)                                       \
do {                                                                 \
    if (__builtin_expect((valI[0] & 1) != 0, true)) {                \
        resop (valI[0] >> 1);                                        \
        valI += 1;                                                   \
    } else if (__builtin_expect((valI[0] & 2) != 0, true)) {         \
        resop (((*(const uint32_t *) (const void *)valI) >> 2) & ((1 << 14) - 1)); \
        valI += 2;                                                   \
    } else if (__builtin_expect((valI[0] & 4) != 0, true)) {         \
        resop (((*(const uint32_t *) (const void *)valI) >> 3) & ((1 << 21) - 1)); \
        valI += 3;                                                   \
    } else {                                                         \
        resop ((*(const uint32_t *) (const void *) valI) >> 4);       \
        valI += 4;                                                   \
    }                                                                \
} while (0)

FakeZcbFilterOcc::FakeZcbFilterOcc(const FakeWord &fw)
    : FakePosting(fw.getName() + ".zcbfilterocc"),
      _compressed(),
      _docIdLimit(0),
      _hitDocs(0)
{
    std::vector<uint8_t> bytes;
    uint32_t lastDocId = 0u;

    auto d = fw._postings.begin();
    auto de = fw._postings.end();

    while (d != de) {
        if (lastDocId == 0u) {
            zcbEncode(bytes, d->_docId - 1);
        } else {
            uint32_t docIdDelta = d->_docId - lastDocId;
            zcbEncode(bytes, docIdDelta - 1);
        }
        lastDocId = d->_docId;
        ++d;
    }
    // 3 padding bytes to ensure ZCBDECODE reads initialized memory.
    bytes.push_back(0);
    bytes.push_back(0);
    bytes.push_back(0);
    _hitDocs = fw._postings.size();
    std::swap(_compressed, bytes);
    _docIdLimit = fw._docIdLimit;
}


FakeZcbFilterOcc::~FakeZcbFilterOcc()
{
}


void
FakeZcbFilterOcc::forceLink()
{
}


size_t
FakeZcbFilterOcc::bitSize() const
{
    // Do not count the 3 padding bytes here.
    return 8 * (_compressed.size() - 3) ;
}

bool
FakeZcbFilterOcc::hasWordPositions() const
{
    return false;
}


int
FakeZcbFilterOcc::lowLevelSinglePostingScan() const
{
    return 0;
}


int
FakeZcbFilterOcc::lowLevelSinglePostingScanUnpack() const
{
    return 0;
}


int
FakeZcbFilterOcc::
lowLevelAndPairPostingScan(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


int
FakeZcbFilterOcc::
lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const
{
    (void) rhs;
    return 0;
}


class FakeFilterOccZCBArrayIterator
    : public queryeval::RankedSearchIteratorBase
{
private:

    FakeFilterOccZCBArrayIterator(const FakeFilterOccZCBArrayIterator &other);

    FakeFilterOccZCBArrayIterator&
    operator=(const FakeFilterOccZCBArrayIterator &other);

public:
    // Pointer to compressed data
    const uint8_t *_valI;
    unsigned int _residue;

    FakeFilterOccZCBArrayIterator(const uint8_t *compressedOccurrences,
                                  unsigned int residue,
                                  const fef::TermFieldMatchDataArray &matchData);

    ~FakeFilterOccZCBArrayIterator() override;

    void doUnpack(uint32_t docId) override;
    void doSeek(uint32_t docId) override;
    void initRange(uint32_t begin, uint32_t end) override;
    Trinary is_strict() const override { return Trinary::True; }
};


FakeFilterOccZCBArrayIterator::
FakeFilterOccZCBArrayIterator(const uint8_t *compressedOccurrences,
                              unsigned int residue,
                              const fef::TermFieldMatchDataArray &matchData)
    : queryeval::RankedSearchIteratorBase(matchData),
      _valI(compressedOccurrences),
      _residue(residue)
{
    clearUnpacked();
}

void
FakeFilterOccZCBArrayIterator::initRange(uint32_t begin, uint32_t end)
{
    queryeval::RankedSearchIteratorBase::initRange(begin, end);
    uint32_t docId = 0;
    if (_residue > 0) {
        ZCBDECODE(_valI, docId = 1 +);
        setDocId(docId);
    } else {
        setAtEnd();
    }
}


FakeFilterOccZCBArrayIterator::~FakeFilterOccZCBArrayIterator()
{
}


void
FakeFilterOccZCBArrayIterator::doSeek(uint32_t docId)
{
    const uint8_t *oCompr = _valI;
    uint32_t oDocId = getDocId();

    if (getUnpacked())
        clearUnpacked();
    while (oDocId < docId) {
        if (--_residue == 0)
            goto atbreak;
        ZCBDECODE(oCompr, oDocId += 1 +);
    }
    _valI = oCompr;
    setDocId(oDocId);
    return;
 atbreak:
    _valI = oCompr;
    setAtEnd();           // Mark end of data
    return;
}


void
FakeFilterOccZCBArrayIterator::doUnpack(uint32_t docId)
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
FakeZcbFilterOcc::
createIterator(const fef::TermFieldMatchDataArray &matchData) const
{
    const uint8_t *arr = &*_compressed.begin();
    return std::make_unique<FakeFilterOccZCBArrayIterator>(arr, _hitDocs,  matchData);
}

}
