// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchiteratorverifier.h"
#include "docid_iterator.h"
#include "initrange.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/truesearch.h>
#include <vespa/searchlib/queryeval/termwise_search.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <set>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;

namespace search::test {
using namespace search::queryeval;
using std::make_unique;

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
    GTEST_DO(verifyTermwise());
    GTEST_DO(verifyInitRange());
}

void
SearchIteratorVerifier::verifyTermwise() const {
    GTEST_DO(verify_and_hits_into(*create(false), _docIds));
    GTEST_DO(verify_and_hits_into(*create(true), _docIds));
    GTEST_DO(verify_or_hits_into(*create(false), _docIds));
    GTEST_DO(verify_or_hits_into(*create(true), _docIds));
    GTEST_DO(verify_get_hits(*create(false), _docIds));
    GTEST_DO(verify_get_hits(*create(true), _docIds));
    GTEST_DO(verify(false));
    GTEST_DO(verify(true));
}

void
SearchIteratorVerifier::verifyInitRange() const {
    InitRangeVerifier initRangeTest;
    GTEST_DO(initRangeTest.verify(*create(false)));
    GTEST_DO(initRangeTest.verify(*create(true)));
}

void
SearchIteratorVerifier::verify_get_hits(bool strict) const {
    constexpr const size_t FIRST_LEGAL = 61;
    SearchIterator::UP iterator = create(strict);
    iterator->initRange(1, getDocIdLimit());
    EXPECT_TRUE(iterator->seek(FIRST_LEGAL));
    EXPECT_EQ(FIRST_LEGAL, iterator->getDocId());
    BitVector::UP hits = iterator->get_hits(1);
    for (size_t i(hits->getStartIndex()); i < FIRST_LEGAL; i++) {
        EXPECT_FALSE(hits->testBit(i));
    }
    EXPECT_TRUE(hits->testBit(FIRST_LEGAL));
}

void
SearchIteratorVerifier::verify(bool strict) const {
    SearchIterator::UP iterator = create(strict);
    GTEST_DO(verify(*iterator, strict, _docIds));
    GTEST_DO(verifyTermwise(std::move(iterator), strict, _docIds));
    GTEST_DO(verifyAnd(strict));
    GTEST_DO(verifyOr(strict));
    GTEST_DO(verifyAndNot(strict));
    GTEST_DO(verify_get_hits(strict));
}

void
SearchIteratorVerifier::verifyAnd(bool strict) const {
    fef::TermFieldMatchData tfmd;
    MultiSearch::Children children;
    children.push_back(create(strict));
    children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, false));
    auto search = AndSearch::create(std::move(children), strict, UnpackInfo());
    GTEST_DO(verify(*search, strict, _expectedAnd));
    GTEST_DO(verifyTermwise(std::move(search), strict, _expectedAnd));
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
            GTEST_DO(verify(*search, strict, _expectedAndNotPositive));
            GTEST_DO(verifyTermwise(std::move(search), strict, _expectedAndNotPositive));
        }
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, true));
        children.push_back(create(strict));
        auto search = AndNotSearch::create(std::move(children), strict);
        GTEST_DO(verify(*search, strict, _expectedAndNotNegative));
        GTEST_DO(verifyTermwise(std::move(search), strict, _expectedAndNotNegative));
    }
}


void
SearchIteratorVerifier::verifyOr(bool strict) const {
    fef::TermFieldMatchData tfmd;
    MultiSearch::Children children;
    children.push_back(create(strict));
    children.push_back(BitVectorIterator::create(_everyOddBitSet.get(), getDocIdLimit(), tfmd, strict));
    SearchIterator::UP search(OrSearch::create(std::move(children), strict, UnpackInfo()));
    GTEST_DO(verify(*search, strict, _expectedOr));
    GTEST_DO(verifyTermwise(std::move(search), strict, _expectedOr));
}


void
SearchIteratorVerifier::verifyTermwise(SearchIterator::UP iterator, bool strict, const DocIds & docIds) {
    SearchIterator::UP termwise = make_termwise(std::move(iterator), strict);
    GTEST_DO(verify(*termwise, strict, docIds));
}

void
SearchIteratorVerifier::verify_and_hits_into(SearchIterator & iterator, const DocIds & docIds) {
    BitVector::UP allSet = BitVector::create(1, getDocIdLimit());
    allSet->notSelf();
    EXPECT_EQ(allSet->countTrueBits(), getDocIdLimit()-1);
    iterator.initRange(1, getDocIdLimit());
    iterator.and_hits_into(*allSet, 1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(allSet->testBit(docIds[i]));
    }
    EXPECT_EQ(allSet->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify_or_hits_into(SearchIterator & iterator, const DocIds & docIds) {
    BitVector::UP noneSet = BitVector::create(1, getDocIdLimit());
    EXPECT_EQ(noneSet->countTrueBits(), 0u);
    iterator.initRange(1, getDocIdLimit());
    iterator.or_hits_into(*noneSet, 1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(noneSet->testBit(docIds[i]));
    }
    EXPECT_EQ(noneSet->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify_get_hits(SearchIterator & iterator, const DocIds & docIds) {
    iterator.initRange(1, getDocIdLimit());
    BitVector::UP result = iterator.get_hits(1);
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_TRUE(result->testBit(docIds[i]));
    }
    EXPECT_EQ(result->countTrueBits(), docIds.size());
}

void
SearchIteratorVerifier::verify(SearchIterator & iterator, bool strict, const DocIds & docIds)
{
    GTEST_DO(verify(iterator, Ranges({{1, getDocIdLimit()}}), strict, docIds));
    GTEST_DO(verify(iterator, Ranges({{1, getDocIdLimit()}}), strict, docIds));
    for (uint32_t rangeWidth : { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 100, 202 }) {
        Ranges ranges;
        for (uint32_t sum(1); sum < getDocIdLimit(); sum += rangeWidth) {
            ranges.emplace_back(sum, std::min(sum+rangeWidth, getDocIdLimit()));
        }
        GTEST_DO(verify(iterator, ranges, strict, docIds));
        std::reverse(ranges.begin(), ranges.end());
        GTEST_DO(verify(iterator, ranges, strict, docIds));
    }
}

void
SearchIteratorVerifier::verify(SearchIterator & iterator, const Ranges & ranges, bool strict, const DocIds & docIds)
{
    DocIds result = search(iterator, ranges, strict);
    ASSERT_EQ(docIds.size(), result.size());
    for (size_t i(0); i < docIds.size(); i++) {
        EXPECT_EQ(docIds[i], result[i]);
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

std::unique_ptr<SearchIterator>
SearchIteratorVerifier::create(bool, const fef::TermFieldMatchDataArray&) const
{
    return {};
}

void
SearchIteratorVerifier::verify_hidden_from_ranking(const fef::TermFieldMatchDataArray& tfmda) const
{
    auto* tfmd = tfmda[0];
    auto iterator = create(true, tfmda);
    ASSERT_TRUE(iterator);
    iterator->initFullRange();
    iterator->seek(_docIds[0]);
    EXPECT_FALSE(iterator->isAtEnd());
    EXPECT_EQ(_docIds[0], iterator->getDocId());
    EXPECT_FALSE(tfmd->has_data(iterator->getDocId()));
    iterator->unpack(iterator->getDocId());
    EXPECT_TRUE(tfmd->has_ranking_data(iterator->getDocId()));
    tfmd->set_hidden_from_ranking();
    EXPECT_FALSE(tfmd->has_ranking_data(iterator->getDocId()));
    iterator->unpack(iterator->getDocId());
    EXPECT_TRUE(tfmd->has_ranking_data(iterator->getDocId()));
    tfmd->set_hidden_from_ranking();
    EXPECT_FALSE(tfmd->has_ranking_data(iterator->getDocId()));
    iterator->seek(_docIds[1]);
    EXPECT_EQ(_docIds[1], iterator->getDocId());
    EXPECT_FALSE(tfmd->has_ranking_data(iterator->getDocId()));
    iterator->unpack(iterator->getDocId());
    EXPECT_TRUE(tfmd->has_ranking_data(iterator->getDocId()));
}

}
