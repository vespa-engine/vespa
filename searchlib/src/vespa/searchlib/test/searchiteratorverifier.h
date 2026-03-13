// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vector>

namespace search::fef { class TermFieldMatchDataArray; }

namespace search::test {

class SearchIteratorVerifier {
public:
    using SearchIterator = queryeval::SearchIterator;
    using DocIds = std::vector<uint32_t>;
    using Range = std::pair<uint32_t, uint32_t>;
    using Ranges = std::vector<Range>;

    static SearchIterator::UP createIterator(const DocIds &docIds, bool strict);
    static SearchIterator::UP createEmptyIterator();
    SearchIterator::UP createFullIterator() const;

    SearchIteratorVerifier();
    virtual ~SearchIteratorVerifier();
    void verify() const;
    void verify_hidden_from_ranking(const fef::TermFieldMatchDataArray& tfmda) const;
    virtual SearchIterator::UP create(bool strict) const = 0;
    virtual std::unique_ptr<SearchIterator> create(bool strict, const fef::TermFieldMatchDataArray& tfmda) const;
    const DocIds & getExpectedDocIds() const { return _docIds; }
    static uint32_t getDocIdLimit() { return 207; }
private:
    void verifyTermwise() const;
    void verifyInitRange() const;
    void verify(bool strict) const;
    void verifyAnd(bool strict) const;
    void verifyAndNot(bool strict) const;
    void verifyOr(bool strict) const;
    void verify_get_hits(bool strict) const;
    static void verifyTermwise(SearchIterator::UP iterator, bool strict, const DocIds & docIds);
    static void verify(SearchIterator & iterator, const DocIds & docIds);
    static void verify(SearchIterator & iterator, bool strict, const DocIds & docIds);
    static void verify(SearchIterator & iterator, const Ranges & ranges, bool strict, const DocIds & docIds);
    static void verify_and_hits_into(SearchIterator & iterator, const DocIds & docIds);
    static void verify_or_hits_into(SearchIterator & iterator, const DocIds & docIds);
    static void verify_get_hits(SearchIterator & iterator, const DocIds & docIds);
    static DocIds search(SearchIterator & iterator, const Ranges & ranges, bool strict);
    static DocIds searchRelaxed(SearchIterator & search, Range range);
    static DocIds searchStrict(SearchIterator & search, Range range);
    mutable search::fef::TermFieldMatchData _trueTfmd;
    DocIds _docIds;
    DocIds _expectedAnd;
    DocIds _expectedOr;
    DocIds _expectedAndNotPositive;
    DocIds _expectedAndNotNegative;

    std::unique_ptr<BitVector> _everyOddBitSet;
};

}
