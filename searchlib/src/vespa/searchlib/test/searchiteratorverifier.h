// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vector>

namespace search {
namespace test {

class SearchIteratorVerifier {
public:
    typedef queryeval::SearchIterator SearchIterator;
    typedef std::vector<uint32_t> DocIds;
    typedef std::pair<uint32_t, uint32_t> Range;
    typedef std::vector<Range> Ranges;

    SearchIteratorVerifier();
    virtual ~SearchIteratorVerifier();
    void verify() const;
    virtual SearchIterator::UP create(bool strict) const = 0;
protected:
    const DocIds & getExpectedDocIds() const { return _docIds; }
    static uint32_t getDocIdLimit() { return 207; }
private:
    void verifyTermwise() const;
    void verifyInitRange() const;
    SearchIterator::UP createIterator(const DocIds &docIds, bool strict) const;
    void verify(bool strict) const;
    void verifyAnd(bool strict) const;
    void verifyOr(bool strict) const;
    static void verifyTermwise(SearchIterator::UP iterator, bool strict, const DocIds & docIds);
    static void verify(SearchIterator & iterator, const DocIds & docIds);
    static void verify(SearchIterator & iterator, bool strict, const DocIds & docIds);
    static void verify(SearchIterator & iterator, const Ranges & ranges, bool strict, const DocIds & docIds);
    static DocIds search(SearchIterator & iterator, const Ranges & ranges, bool strict);
    static DocIds searchRelaxed(SearchIterator & search, Range range);
    static DocIds searchStrict(SearchIterator & search, Range range);
    mutable search::fef::TermFieldMatchData _trueTfmd;
    DocIds _docIds;
    DocIds _expectedAnd;
    DocIds _expectedOr;
    std::unique_ptr<BitVector> _everyOddBitSet;
};

}
}
