// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakeword.h"

#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/vespalib/util/size_literals.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;
using search::index::WordDocElementFeatures;
using search::index::WordDocElementWordPosFeatures;
using search::index::PostingListFileSeqWrite;
using search::index::DocIdAndFeatures;
using search::index::DocIdAndPosOccFeatures;
using search::index::PostingListCounts;
using search::index::PostingListFileSeqRead;
using search::diskindex::FieldReader;
using search::diskindex::FieldWriter;

namespace search
{

namespace fakedata
{


static void
fillbitset(search::BitVector *bitvector,
           unsigned int size,
           vespalib::Rand48 &rnd)
{
    unsigned int range;
    unsigned int idx;
    unsigned int j;

    range = bitvector->size();
    assert(range > 0);
    --range;
    bitvector->invalidateCachedCount();

    assert(size <= range);
    if (size > range / 2) {
        if (range > 0)
            bitvector->setInterval(1, range);

        for (j = range; j > size; --j) {
            do {
                idx = (rnd.lrand48() % range) + 1u;
            } while (!bitvector->testBit(idx));
            bitvector->clearBit(idx);
        }
    } else {
        // bitvector->reset();
        bitvector->invalidateCachedCount();
        for (j = bitvector->countTrueBits(); j < size; j++) {
            do {
                idx = (rnd.lrand48() % range) + 1u;
            } while (bitvector->testBit(idx));
            bitvector->setBit(idx);
        }
    }
}


static void
fillcorrelatedbitset(search::BitVector &bitvector,
                     unsigned int size,
                     const FakeWord &otherword,
                     vespalib::Rand48 &rnd)
{
    const FakeWord::DocWordFeatureList &opostings = otherword._postings;

    unsigned int range = opostings.size();
    search::BitVector::UP corrmap(search::BitVector::create(range + 1));

    if (size > range)
        size = range;
    fillbitset(corrmap.get(), size, rnd);

    unsigned int idx = corrmap->getNextTrueBit(1u);
    while (idx < range) {
        unsigned int docId = opostings[idx - 1]._docId;
        bitvector.setBit(docId);
        ++idx;
        if (idx > range)
            break;
        idx = corrmap->getNextTrueBit(idx);
    }
}


FakeWord::DocWordPosFeature::DocWordPosFeature()
    : _elementId(0),
      _wordPos(0),
      _elementWeight(1),
      _elementLen(0)
{
}


FakeWord::DocWordPosFeature::~DocWordPosFeature()
{
}


FakeWord::DocWordCollapsedFeature::DocWordCollapsedFeature()
    : _field_len(0),
      _num_occs(0)
{
}

FakeWord::DocWordCollapsedFeature::~DocWordCollapsedFeature() = default;

FakeWord::DocWordFeature::DocWordFeature()
    : _docId(0),
      _collapsedDocWordFeatures(),
      _positions(0),
      _accPositions(0)
{
}

FakeWord::DocWordFeature::~DocWordFeature()
{
}

FakeWord::FakeWord(uint32_t docIdLimit,
                   const std::vector<uint32_t> & docIds,
                   const std::string &name,
                   const PosOccFieldsParams &fieldsParams,
                   uint32_t packedIndex)
    : _postings(),
      _wordPosFeatures(),
      _extraPostings(),
      _extraWordPosFeatures(),
      _docIdLimit(docIdLimit),
      _name(name),
      _fieldsParams(fieldsParams),
      _packedIndex(packedIndex)
{
    search::BitVector::UP bitmap(search::BitVector::create(docIdLimit));
    for (uint32_t docId : docIds) {
        bitmap->setBit(docId);
    }
    vespalib::Rand48 rnd;
    fakeup(*bitmap, rnd, _postings, _wordPosFeatures);
}

FakeWord::FakeWord(uint32_t docIdLimit,
                   uint32_t wordDocs,
                   uint32_t tempWordDocs,
                   const std::string &name,
                   vespalib::Rand48 &rnd,
                   const PosOccFieldsParams &fieldsParams,
                   uint32_t packedIndex)
    : _postings(),
      _wordPosFeatures(),
      _extraPostings(),
      _extraWordPosFeatures(),
      _docIdLimit(docIdLimit),
      _name(name),
      _fieldsParams(fieldsParams),
      _packedIndex(packedIndex)
{
    search::BitVector::UP bitmap(search::BitVector::create(docIdLimit));

    fillbitset(bitmap.get(), wordDocs, rnd);

    fakeup(*bitmap, rnd, _postings, _wordPosFeatures);
    fakeupTemps(rnd, docIdLimit, tempWordDocs);
    setupRandomizer(rnd);
}


FakeWord::FakeWord(uint32_t docIdLimit,
                   uint32_t wordDocs,
                   uint32_t tempWordDocs,
                   const std::string &name,
                   const FakeWord &otherWord,
                   size_t overlapDocs,
                   vespalib::Rand48 &rnd,
                   const PosOccFieldsParams &fieldsParams,
                   uint32_t packedIndex)
    : _postings(),
      _wordPosFeatures(),
      _docIdLimit(docIdLimit),
      _name(name),
      _fieldsParams(fieldsParams),
      _packedIndex(packedIndex)
{
    search::BitVector::UP bitmap(search::BitVector::create(docIdLimit));

    if (wordDocs * 2 < docIdLimit &&
        overlapDocs > 0)
        fillcorrelatedbitset(*bitmap, overlapDocs, otherWord, rnd);
    fillbitset(bitmap.get(), wordDocs, rnd);

    fakeup(*bitmap, rnd, _postings, _wordPosFeatures);
    fakeupTemps(rnd, docIdLimit, tempWordDocs);
    setupRandomizer(rnd);
}


FakeWord::~FakeWord()
{
}


void
FakeWord::fakeup(search::BitVector &bitmap,
                 vespalib::Rand48 &rnd,
                 DocWordFeatureList &postings,
                 DocWordPosFeatureList &wordPosFeatures)
{
    DocWordPosFeatureList wpf;
    unsigned int idx;
    uint32_t numFields = _fieldsParams.getNumFields();
    assert(numFields == 1u);
    (void) numFields;
    uint32_t docIdLimit = bitmap.size();
    idx = bitmap.getNextTrueBit(1u);
    while (idx < docIdLimit) {
        DocWordFeature dwf;
        unsigned int positions;

        dwf._docId = idx;
        positions = ((rnd.lrand48() % 10) == 0) ? 2 : 1;
        dwf._positions = positions;
        wpf.clear();
        for (unsigned int j = 0; j < positions; ++j) {
            DocWordPosFeature dwpf;
            dwpf._wordPos = rnd.lrand48() % 8_Ki;
            dwpf._elementId = 0;
            if (_fieldsParams.getFieldParams()[0]._hasElements) {
                dwpf._elementId = rnd.lrand48() % 4;
            }
            wpf.push_back(dwpf);
        }
        if (positions > 1) {
            /* Sort wordpos list and "avoid" duplicate positions */
            std::sort(wpf.begin(), wpf.end());
        }
        uint32_t field_len = 0;
        do {
            auto ie = wpf.end();
            auto i = wpf.begin();
            while (i != ie) {
                uint32_t lastwordpos = i->_wordPos;
                auto pi = i;
                ++i;
                while (i != ie &&
                       pi->_elementId == i->_elementId) {
                    if (i->_wordPos <= lastwordpos)
                        i->_wordPos = lastwordpos + 1;
                    lastwordpos = i->_wordPos;
                    ++i;
                }
                uint32_t elementLen = (rnd.lrand48() % 8_Ki) + 1 + lastwordpos;
                int32_t elementWeight = 1;
                if (_fieldsParams.getFieldParams()[0].
                    _hasElementWeights) {
                    uint32_t uWeight = rnd.lrand48() % 2001;
                    if ((uWeight & 1) != 0)
                        elementWeight = - (uWeight >> 1) - 1;
                    else
                        elementWeight = (uWeight >> 1);
                    assert(elementWeight <= 1000);
                    assert(elementWeight >= -1000);
                }
                while (pi != i) {
                    pi->_elementLen = elementLen;
                    pi->_elementWeight = elementWeight;
                    ++pi;
                }
                field_len += elementLen;
            }
            if (_fieldsParams.getFieldParams()[0]._hasElements) {
                field_len += ((rnd.lrand48() % 10) + 10);
            }
        } while (0);
        dwf._collapsedDocWordFeatures._field_len = field_len;
        dwf._collapsedDocWordFeatures._num_occs = dwf._positions;
        dwf._accPositions = wordPosFeatures.size();
        assert(dwf._positions == wpf.size());
        postings.push_back(dwf);
        for (const auto& elem : wpf) {
            wordPosFeatures.push_back(elem);
        }
        ++idx;
        if (idx >= docIdLimit)
            break;
        idx = bitmap.getNextTrueBit(idx);
    }
}


void
FakeWord::fakeupTemps(vespalib::Rand48 &rnd,
                      uint32_t docIdLimit,
                      uint32_t tempWordDocs)
{
    uint32_t maxTempWordDocs = docIdLimit / 2;
    tempWordDocs = std::min(tempWordDocs, maxTempWordDocs);
    if (tempWordDocs > 0) {
        search::BitVector::UP bitmap(search::BitVector::create(docIdLimit));
        fillbitset(bitmap.get(), tempWordDocs, rnd);
        fakeup(*bitmap, rnd, _extraPostings, _extraWordPosFeatures);
    }
}

void
FakeWord::setupRandomizer(vespalib::Rand48 &rnd)
{
    Randomizer randomAdd;
    Randomizer randomRem;

    auto d = _postings.begin();
    auto de = _postings.end();
    int32_t ref = 0;

    while (d != de) {
        do {
            randomAdd._random = rnd.lrand48();
        } while (randomAdd._random < 10000);
        randomAdd._ref = ref;
        assert(!randomAdd.isExtra());
        assert(!randomAdd.isRemove());
        _randomizer.push_back(randomAdd);
        ++d;
        ++ref;
    }

    auto ed = _extraPostings.begin();
    auto ede = _extraPostings.end();

    int32_t eref = -1;
    uint32_t tref = 0;
    ref = 0;
    int32_t refmax = _randomizer.size();
    while (ed != ede) {
        while (ref < refmax && _postings[ref]._docId < ed->_docId)
            ++ref;
        if (ref < refmax && _postings[ref]._docId == ed->_docId) {
            randomAdd._random = rnd.lrand48() % (_randomizer[ref]._random - 1);
            randomRem._random = _randomizer[ref]._random - 1;
        } else {
            do {
                randomAdd._random = rnd.lrand48();
                randomRem._random = rnd.lrand48();
            } while (randomAdd._random >= randomRem._random);
        }
        randomAdd._ref = eref;
        randomRem._ref = eref - 1;
        assert(randomAdd.isExtra());
        assert(!randomAdd.isRemove());
        assert(randomAdd.extraIdx() == tref);
        assert(randomRem.isExtra());
        assert(randomRem.isRemove());
        assert(randomRem.extraIdx() == tref);
        _randomizer.push_back(randomAdd);
        _randomizer.push_back(randomRem);
        ++ed;
        eref -= 2;
        ++tref;
    }
    std::sort(_randomizer.begin(), _randomizer.end());
}


void
FakeWord::addDocIdBias(uint32_t docIdBias)
{
    auto d = _postings.begin();
    auto de = _postings.end();
    for (; d != de; ++d) {
        d->_docId += docIdBias;
    }
    d = _extraPostings.begin();
    de = _extraPostings.end();
    for (; d != de; ++d) {
        d->_docId += docIdBias;
    }
    _docIdLimit += docIdBias;
}


bool
FakeWord::validate(search::queryeval::SearchIterator *iterator,
                   const fef::TermFieldMatchDataArray &matchData,
                   uint32_t stride,
                   bool unpack_normal_features,
                   bool unpack_interleaved_features,
                   bool verbose) const
{
    iterator->initFullRange();
    uint32_t docId = 0;

    using TMDPI = TermFieldMatchData::PositionsIterator;

    auto d = _postings.begin();
    auto de = _postings.end();
    auto p = _wordPosFeatures.begin();
    auto pe = _wordPosFeatures.end();

    if (verbose)
        printf("Start validate word '%s'\n", _name.c_str());
    int strideResidue = stride;
    while (d != de) {
        if (strideResidue > 1) {
            --strideResidue;
            unsigned int positions = d->_positions;
            while (positions > 0) {
                ++p;
                --positions;
            }
        } else {
            strideResidue = stride;
            docId = d->_docId;
            bool seekRes = iterator->seek(docId);
            assert(seekRes);
            (void) seekRes;
            assert(d != de);
            unsigned int positions = d->_positions;
            iterator->unpack(docId);
            for (size_t lfi = 0; lfi < matchData.size(); ++lfi) {
                if (!matchData[lfi]->has_data(docId)) {
                    continue;
                }
                if (unpack_interleaved_features) {
                    assert(d->_collapsedDocWordFeatures._field_len == matchData[lfi]->getFieldLength());
                    assert(d->_collapsedDocWordFeatures._num_occs == matchData[lfi]->getNumOccs());
                } else {
                    assert(matchData[lfi]->getFieldLength() == 0u);
                    assert(matchData[lfi]->getNumOccs() == 0u);
                }
                if (unpack_normal_features) {
                    TMDPI mdpe = matchData[lfi]->end();
                    TMDPI mdp = matchData[lfi]->begin();
                    while (mdp != mdpe) {
                        assert(p != pe);
                        assert(positions > 0);
                        assert(p->_wordPos == mdp->getPosition());
                        assert(p->_elementId == mdp->getElementId());
                        assert(p->_elementWeight == mdp->getElementWeight());
                        assert(p->_elementLen == mdp->getElementLen());
                        ++p;
                        ++mdp;
                        --positions;
                    }
                } else {
                    assert(matchData[lfi]->size() == 0u);
                }
            }
            assert(positions == 0 || !unpack_normal_features);
        }
        ++d;
    }
    assert(p == pe || !unpack_normal_features);
    assert(d == de);
    if (verbose)
        printf("word '%s' validated successfully with unpack\n",
               _name.c_str());
    return true;
}


bool
FakeWord::validate(search::queryeval::SearchIterator *iterator,
                   const fef::TermFieldMatchDataArray &matchData,
                   bool unpack_normal_features,
                   bool unpack_interleaved_features,
                   bool verbose) const
{
    iterator->initFullRange();
    uint32_t docId = 1;

    using TMDPI = TermFieldMatchData::PositionsIterator;

    auto d = _postings.begin();
    auto de = _postings.end();
    auto p = _wordPosFeatures.begin();
    auto pe = _wordPosFeatures.end();

    if (verbose)
        printf("Start validate word '%s'\n", _name.c_str());
    for (;;) {
        if (iterator->seek(docId)) {
            assert(d != de);
            assert(d->_docId == docId);
            iterator->unpack(docId);
            unsigned int positions = d->_positions;
            for (size_t lfi = 0; lfi < matchData.size(); ++lfi) {
                if (!matchData[lfi]->has_data(docId)) {
                    continue;
                }
                if (unpack_interleaved_features) {
                    assert(d->_collapsedDocWordFeatures._field_len == matchData[lfi]->getFieldLength());
                    assert(d->_collapsedDocWordFeatures._num_occs == matchData[lfi]->getNumOccs());
                } else {
                    assert(matchData[lfi]->getFieldLength() == 0u);
                    assert(matchData[lfi]->getNumOccs() == 0u);
                }
                if (unpack_normal_features) {
                    TMDPI mdpe = matchData[lfi]->end();
                    TMDPI mdp = matchData[lfi]->begin();
                    while (mdp != mdpe) {
                        assert(p != pe);
                        assert(positions > 0);
                        assert(p->_wordPos == mdp->getPosition());
                        assert(p->_elementId == mdp->getElementId());
                        assert(p->_elementWeight == mdp->getElementWeight());
                        assert(p->_elementLen == mdp->getElementLen());
                        ++p;
                        ++mdp;
                        --positions;
                    }
                } else {
                    assert(matchData[lfi]->size() == 0u);
                }
            }
            assert(positions == 0 || !unpack_normal_features);
            ++d;
            ++docId;
        } else {
            if (iterator->getDocId() > docId)
                docId = iterator->getDocId();
            else
                ++docId;
        }
        if (docId >= _docIdLimit)
            break;
    }
    assert(p == pe || !unpack_normal_features);
    assert(d == de);
    if (verbose)
        printf("word '%s' validated successfully with unpack\n",
               _name.c_str());
    return true;
}


bool
FakeWord::validate(search::queryeval::SearchIterator *iterator, bool verbose) const
{
    iterator->initFullRange();
    uint32_t docId = 1;

    auto d = _postings.begin();
    auto de = _postings.end();

    if (verbose)
        printf("Start validate word '%s'\n", _name.c_str());
    for (;;) {
        if (iterator->seek(docId)) {
            assert(d != de);
            assert(d->_docId == docId);
            ++d;
            ++docId;
        } else {
            if (iterator->getDocId() > docId)
                docId = iterator->getDocId();
            else
                ++docId;
        }
        if (docId >= _docIdLimit)
            break;
    }
    assert(d == de);
    if (verbose)
        printf("word '%s' validated successfully without unpack\n",
               _name.c_str());
    return true;
}


bool
FakeWord::validate(FieldReader &fieldReader,
                   uint32_t wordNum,
                   const fef::TermFieldMatchDataArray &matchData,
                   bool decode_interleaved_features,
                   bool verbose) const
{
    uint32_t docId = 0;
    uint32_t numDocs;
    uint32_t residue;
    uint32_t presidue;
    bool unpres;

    using TMDPI = TermFieldMatchData::PositionsIterator;

    auto d = _postings.begin();
    auto de = _postings.end();
    auto p = _wordPosFeatures.begin();
    auto pe = _wordPosFeatures.end();

    if (verbose)
        printf("Start validate word '%s'\n", _name.c_str());
#ifdef notyet
    // Validate word number
#else
    (void) wordNum;
#endif
    numDocs = _postings.size();
    for (residue = numDocs; residue > 0; --residue) {
        assert(fieldReader._wordNum == wordNum);
        DocIdAndFeatures &features(fieldReader._docIdAndFeatures);
        docId = features.doc_id();
        assert(d != de);
        assert(d->_docId == docId);
        if (decode_interleaved_features) {
            assert(d->_collapsedDocWordFeatures._field_len == features.field_length());
            assert(d->_collapsedDocWordFeatures._num_occs == features.num_occs());
        }
        if (matchData.valid()) {
#ifdef notyet
            unpres = features.unpack(matchData);
            assert(unpres);
#else
            (void) unpres;

            auto element = features.elements().begin();
            auto position = features.word_positions().begin();

            TermFieldMatchData *tfmd = matchData[0];
            assert(tfmd != nullptr);
            tfmd->reset(features.doc_id());

            uint32_t elementResidue = features.elements().size();
            while (elementResidue != 0) {
                uint32_t positionResidue = element->getNumOccs();
                while (positionResidue != 0) {
                    uint32_t wordPos = position->getWordPos();
                    TermFieldMatchDataPosition pos(element->getElementId(),
                                                   wordPos,
                                                   element->getWeight(),
                                                   element->getElementLen());
                    tfmd->appendPosition(pos);
                    ++position;
                    --positionResidue;
                }
                ++element;
                --elementResidue;
            }
#endif
            unsigned int positions = d->_positions;
            presidue = positions;
            for (size_t lfi = 0; lfi < matchData.size(); ++lfi) {
                if (!matchData[lfi]->has_data(docId)) {
                    continue;
                }
                TMDPI mdpe = matchData[lfi]->end();
                TMDPI mdp = matchData[lfi]->begin();
                while (mdp != mdpe) {
                    assert(p != pe);
                    assert(presidue > 0);
                    assert(p->_wordPos == mdp->getPosition());
                    assert(p->_elementId == mdp->getElementId());
                    assert(p->_elementWeight == mdp->getElementWeight());
                    assert(p->_elementLen == mdp->getElementLen());
                    ++p;
                    ++mdp;
                    --presidue;
                }
            }
            assert(presidue == 0);
            ++d;
        }
        fieldReader.read();
    }
    if (matchData.valid()) {
        assert(p == pe);
        assert(d == de);
    }
    if (verbose)
        printf("word '%s' validated successfully %s unpack\n",
               _name.c_str(),
               matchData.valid() ? "with" : "without");
    return true;
}


void
FakeWord::validate(const std::vector<uint32_t> &docIds) const
{
    auto d = _postings.begin();
    auto de = _postings.end();
    auto di = docIds.begin();
    auto die = docIds.end();

    while (d != de) {
        assert(di != die);
        assert(d->_docId == *di);
        ++d;
        ++di;
    }
    assert(di == die);
}


void
FakeWord::validate(const search::BitVector &bv) const
{
    auto d = _postings.begin();
    auto de = _postings.end();
    uint32_t bitHits = bv.countTrueBits();
    assert(bitHits == _postings.size());
    (void) bitHits;
    uint32_t bi = bv.getNextTrueBit(1u);
    while (d != de) {
        assert(d->_docId == bi);
        ++d;
        bi = bv.getNextTrueBit(bi + 1);
    }
    assert(bi >= bv.size());
}


bool
FakeWord::dump(FieldWriter &fieldWriter,
               bool verbose) const
{
    uint32_t numDocs;
    uint32_t residue;
    DocIdAndPosOccFeatures features;

    auto d = _postings.begin();
    auto de = _postings.end();
    auto p = _wordPosFeatures.begin();
    auto pe = _wordPosFeatures.end();

    if (verbose)
        printf("Start dumping word '%s'\n", _name.c_str());
    numDocs = _postings.size();
    for (residue = numDocs; residue > 0; --residue) {
        assert(d != de);
        setupFeatures(*d, &*p, features);
        p += d->_positions;
        fieldWriter.add(features);
        ++d;
    }
    assert(p == pe);
    assert(d == de);
    if (verbose)
        printf("word '%s' dumped successfully\n",
               _name.c_str());
    return true;
}


FakeWord::RandomizedReader::RandomizedReader()
    : _r(),
      _fw(nullptr),
      _wordIdx(0u),
      _valid(false),
      _ri(),
      _re()
{
}


void
FakeWord::RandomizedReader::read()
{
    if (_ri != _re) {
        _r = *_ri;
        ++_ri;
    } else
        _valid = false;
}


void
FakeWord::RandomizedReader::setup(const FakeWord *fw,
                                  uint32_t wordIdx)
{
    _fw = fw;
    _wordIdx = wordIdx;
    _ri = fw->_randomizer.begin();
    _re = fw->_randomizer.end();
    _valid = _ri != _re;
}


FakeWord::RandomizedWriter::~RandomizedWriter()
{
}


} // namespace fakedata

} // namespace search
