// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vector>

namespace search::test {

#ifdef ENABLE_GTEST_MIGRATION
#define InitRangeVerifier InitRangeVerifierForGTest
#endif

class InitRangeVerifier {
public:
    using SearchIterator = queryeval::SearchIterator;
    using DocIds = std::vector<uint32_t>;
    using Range = std::pair<uint32_t, uint32_t>;
    using Ranges = std::vector<Range>;

    static DocIds invert(const DocIds & docIds, uint32_t docIdlimit);
    static SearchIterator::UP createIterator(const DocIds &docIds, bool strict);
    static SearchIterator::UP createEmptyIterator();
    SearchIterator::UP createFullIterator() const;
    InitRangeVerifier();
    ~InitRangeVerifier();
    const DocIds & getExpectedDocIds() const { return _docIds; }
    uint32_t getDocIdLimit() const { return 207; }
    void verify(SearchIterator & iterator) const;
    /// Convenience that takes ownership of the pointer.
    void verify(SearchIterator * iterator) const;
    void verify(SearchIterator::UP iterator) const { verify(*iterator); }
private:
    void verify(SearchIterator & iterator, bool strict) const;
    void verify(SearchIterator & iterator, const Ranges & ranges, bool strict) const;
    static DocIds search(SearchIterator & iterator, const Ranges & ranges, bool strict);
    static DocIds searchRelaxed(SearchIterator & search, Range range);
    static DocIds searchStrict(SearchIterator & search, Range range);
    mutable search::fef::TermFieldMatchData _trueTfmd;
    DocIds _docIds;
};

}
