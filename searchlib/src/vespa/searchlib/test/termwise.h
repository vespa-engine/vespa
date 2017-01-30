// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search {
namespace test {

class TermwiseVerifier {
public:
    typedef queryeval::SearchIterator SearchIterator;
    typedef std::vector<uint32_t> DocIds;
    typedef std::pair<uint32_t, uint32_t> Range;
    typedef std::vector<Range> Ranges;

    static DocIds invert(const DocIds & docIds, uint32_t docIdlimit);
    SearchIterator::UP createIterator(const DocIds &docIds, bool strict) const;
    SearchIterator::UP createEmptyIterator() const;
    SearchIterator::UP createFullIterator() const;
    TermwiseVerifier();
    ~TermwiseVerifier();
    const DocIds & getExpectedDocIds() const { return _docIds; }
    uint32_t getDocIdLimit() const { return 207; }
    void verify(SearchIterator & iterator) const;
    /// Convenience that takes ownership of the pointer.
    void verify(SearchIterator * iterator) const;
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
}
