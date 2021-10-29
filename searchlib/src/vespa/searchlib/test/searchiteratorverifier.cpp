// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchiteratorverifier.h"
#include "initrange.h"
#ifdef ENABLE_GTEST_MIGRATION
#include <vespa/vespalib/gtest/gtest.h>
#define TEST_DO(x) x
#define EXPECT_EQUAL EXPECT_EQ
#define ASSERT_EQUAL ASSERT_EQ
#else
#include <vespa/vespalib/testkit/test_kit.h>
#endif
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/truesearch.h>
#include <vespa/searchlib/queryeval/termwise_search.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <set>


namespace search::test {

using namespace search::queryeval;
using std::make_unique;

class DocIdIterator : public SearchIterator
{
public:
    DocIdIterator(const SearchIteratorVerifier::DocIds & docIds, bool strict) :
        _strict(strict),
        _currIndex(0),
        _docIds(docIds)
    { }

    void initRange(uint32_t beginId, uint32_t endId) override {
        SearchIterator::initRange(beginId, endId);
        _currIndex = 0;
        if (_strict) {
            doSeek(beginId);
        }
    }

    void doSeek(uint32_t docId) override {
        while ((_currIndex < _docIds.size()) && (_docIds[_currIndex] < docId)) {
            _currIndex++;
        }
        if ((_currIndex < _docIds.size()) && (_docIds[_currIndex] < getEndId())) {
            if (_docIds[_currIndex] == docId || _strict) {
                setDocId(_docIds[_currIndex]);
            }
        } else {
            setAtEnd();
        }
    }

    void doUnpack(uint32_t docid) override { (void) docid; }

    vespalib::Trinary is_strict() const override {
        return _strict ? vespalib::Trinary::True : vespalib::Trinary::False;
    }

private:
    const bool _strict;
    uint32_t   _currIndex;
    const SearchIteratorVerifier::DocIds _docIds;
};

SearchIteratorVerifier::SearchIteratorVerifier() :
    _trueTfmd(),
    _docIds(),
    _everyOddBitSet(BitVector::create(getDocIdLimit()))
{
    for (size_t i(1); i < getDocIdLimit(); i += 2) {
        _everyOddBitSet->setBit(i);
    }
    // (0),1 and 10,11 and 20,21 .... 200,201 etc are hits
    // 0 is of course invalid.
    for (size_t i(0); (i*10+1) < getDocIdLimit(); i++) {
        if (i > 0) {
            _docIds.push_back(i * 10);
        }
        _docIds.push_back(i*10 + 1);
    }
    std::set<uint32_t> orSet;
    for (uint32_t docId : _docIds) {
        orSet.insert(docId);
    }
    _everyOddBitSet->foreach_truebit([&orSet](uint32_t docId){orSet.insert(docId);});
    for (uint32_t docId : orSet) {
        _expectedOr.push_back(docId);
    }
    for (uint32_t docId : _docIds) {
        if (_everyOddBitSet->testBit(docId)) {
            _expectedAnd.push_back(docId);
        }
    }
    for (uint32_t docId : _docIds) {
        if (! _everyOddBitSet->testBit(docId)) {
            _expectedAndNotPositive.push_back(docId);
        }
    }
    std::set<uint32_t> everyOddSet(_docIds.begin(), _docIds.end());
    _everyOddBitSet->foreach_truebit([&](uint32_t docId) { if (everyOddSet.find(docId) == everyOddSet.end()) { _expectedAndNotNegative.emplace_back(docId);} });
}

SearchIterator::UP
SearchIteratorVerifier::createIterator(const DocIds &docIds, bool strict)
{
    return make_unique<DocIdIterator>(docIds, strict);
}

SearchIterator::UP
SearchIteratorVerifier::createEmptyIterator()
{
    return make_unique<EmptySearch>();
}

SearchIterator::UP
SearchIteratorVerifier::createFullIterator() const
{
    return make_unique<TrueSearch>(_trueTfmd);
}
SearchIteratorVerifier::~SearchIteratorVerifier() = default;

void
SearchIteratorVerifier::verify() const {
    TEST_DO(verifyTermwise());
    TEST_DO(verifyInitRange());
}

void
SearchIteratorVerifier::verifyTermwise() const {
    TEST_DO(verify_and_hits_into(*create(false), _docIds));
    TEST_DO(verify_and_hits_into(*create(true), _docIds));
    TEST_DO(verify_or_hits_into(*create(false), _docIds));
    TEST_DO(verify_or_hits_into(*create(true), _docIds));
    TEST_DO(verify_get_hits(*create(false), _docIds));
    TEST_DO(verify_get_hits(*create(true), _docIds));
    TEST_DO(verify(false));
    TEST_DO(verify(true));
}

void
SearchIteratorVerifier::verifyInitRange() const {
    InitRangeVerifier initRangeTest;
    TEST_DO(initRangeTest.verify(*create(false)));
    TEST_DO(initRangeTest.verify(*create(true)));
}

void
SearchIteratorVerifier::verify_get_hits(bool strict) const {
    constexpr const size_t FIRST_LEGAL = 61;
    SearchIterator::UP iterator = create(strict);
    iterator->initRange(1, getDocIdLimit());
    EXPECT_TRUE(iterator->seek(FIRST_LEGAL));
    EXPECT_EQUAL(FIRST_LEGAL, iterator->getDocId());
    BitVector::UP hits = iterator->get_hits(1);
    for (size_t i(0); i < FIRST_LEGAL; i++) {
        EXPECT_FALSE(hits->testBit(i));
    }
    EXPECT_TRUE(hits->testBit(FIRST_LEGAL));
}

void
SearchIteratorVerifier::verify(bool strict) const {
    SearchIterator::UP iterator = create(strict);
    TEST_DO(verify(*iterator, strict, _docIds));
    TEST_DO(verifyTermwise(std::move(iterator), strict, _docIds));
    TEST_DO(verifyAnd(strict));
    TEST_DO(verifyOr(strict));
    TEST_DO(verifyAndNot(strict));
    TEST_DO(verify_get_hits(strict));
}

void
SearchIteratorVerifier::verifyAnd(bool strict) const {
    fef::TermFieldMatchData tfmd;
    MultiSearch::Children children;
    children.push_back(create(strict));
    children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, false));
    auto search = AndSearch::create(std::move(children), strict, UnpackInfo());
    TEST_DO(verify(*search, strict, _expectedAnd));
    TEST_DO(verifyTermwise(std::move(search), strict, _expectedAnd));
}

void
SearchIteratorVerifier::verifyAndNot(bool strict) const {
    fef::TermFieldMatchData tfmd;
    {
        for (bool notStrictness : {false, true}) {
            MultiSearch::Children children;
            children.push_back(create(strict));
            children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, notStrictness));
            auto search = AndNotSearch::create(std::move(children), strict);
            TEST_DO(verify(*search, strict, _expectedAndNotPositive));
            TEST_DO(verifyTermwise(std::move(search), strict, _expectedAndNotPositive));
        }
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, true));
        children.push_back(create(strict));
        auto search = AndNotSearch::create(std::move(children), strict);
        TEST_DO(verify(*search, strict, _expectedAndNotNegative));
        TEST_DO(verifyTermwise(std::move(search), strict, _expectedAndNotNegative));
    }
}


void
SearchIteratorVerifier::verifyOr(bool strict) const {
    fef::TermFieldMatchData tfmd;
    MultiSearch::Children children;
    children.push_back(create(strict));
    children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, strict));
    SearchIterator::UP search(OrSearch::create(std::move(children), strict, UnpackInfo()));
    TEST_DO(verify(*search, strict, _expectedOr));
    TEST_DO(verifyTermwise(std::move(search), strict, _expectedOr));
}


void
SearchIteratorVerifier::verifyTermwise(SearchIterator::UP iterator, bool strict, const DocIds & docIds) {
    SearchIterator::UP termwise = make_termwise(std::move(iterator), strict);
    TEST_DO(verify(*termwise, strict, docIds));
}

void
SearchIteratorVerifier::verify_and_hits_into(SearchIterator & iterator, const DocIds & docIds) {
    BitVector::UP allSet = BitVector::create(1, getDocIdLimit());
    allSet->notSelf();
    EXPECT_EQUAL(allSet->countTrueBits(), getDocIdLimit()-1);
    iterator.initRange(1, getDocIdLimit());
    iterator.and_hits_into(*allSet, 1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(allSet->testBit(docIds[i]));
    }
    EXPECT_EQUAL(allSet->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify_or_hits_into(SearchIterator & iterator, const DocIds & docIds) {
    BitVector::UP noneSet = BitVector::create(1, getDocIdLimit());
    EXPECT_EQUAL(noneSet->countTrueBits(), 0u);
    iterator.initRange(1, getDocIdLimit());
    iterator.or_hits_into(*noneSet, 1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(noneSet->testBit(docIds[i]));
    }
    EXPECT_EQUAL(noneSet->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify_get_hits(SearchIterator & iterator, const DocIds & docIds) {
    iterator.initRange(1, getDocIdLimit());
    BitVector::UP result = iterator.get_hits(1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(result->testBit(docIds[i]));
    }
    EXPECT_EQUAL(result->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify(SearchIterator & iterator, bool strict, const DocIds & docIds)
{
    TEST_DO(verify(iterator, Ranges({{1, getDocIdLimit()}}), strict, docIds));
    TEST_DO(verify(iterator, Ranges({{1, getDocIdLimit()}}), strict, docIds));
    for (uint32_t rangeWidth : { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 202 }) {
        Ranges ranges;
        for (uint32_t sum(1); sum < getDocIdLimit(); sum += rangeWidth) {
            ranges.emplace_back(sum, std::min(sum+rangeWidth, getDocIdLimit()));
        }
        TEST_DO(verify(iterator, ranges, strict, docIds));
        std::reverse(ranges.begin(), ranges.end());
        TEST_DO(verify(iterator, ranges, strict, docIds));
    }
}

void
SearchIteratorVerifier::verify(SearchIterator & iterator, const Ranges & ranges, bool strict, const DocIds & docIds)
{
    DocIds result = search(iterator, ranges, strict);
    ASSERT_EQUAL(docIds.size(), result.size());
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_EQUAL(docIds[i], result[i]);
    }
}

SearchIteratorVerifier::DocIds
SearchIteratorVerifier::search(SearchIterator & it, const Ranges & ranges, bool strict)
{
    DocIds result;
    for (Range range : ranges) {
        DocIds part = strict ? searchStrict(it, range) : searchRelaxed(it, range);
        result.insert(result.end(), part.begin(), part.end());
    }
    std::sort(result.begin(), result.end());
    return result;
}

SearchIteratorVerifier::DocIds
SearchIteratorVerifier::searchRelaxed(SearchIterator & it, Range range)
{
    DocIds result;
    it.initRange(range.first, range.second);
    for (uint32_t docid = range.first; docid < range.second; ++docid) {
        if (it.seek(docid)) {
            result.emplace_back(docid);
        }
    }
    return result;
}

SearchIteratorVerifier::DocIds
SearchIteratorVerifier::searchStrict(SearchIterator & it, Range range)
{
    DocIds result;
    it.initRange(range.first, range.second);
    for (uint32_t docId = it.seekFirst(range.first); (docId < range.second) && !it.isAtEnd(); docId = it.seekNext(docId + 1)) {
        result.push_back(docId);
    }
    return result;
}

}
