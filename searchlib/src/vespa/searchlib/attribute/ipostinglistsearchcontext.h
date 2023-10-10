// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::queryeval {
    class SearchIterator;
    class ExecuteInfo;
}
namespace search::fef { class TermFieldMatchData; }

namespace search::attribute {


/**
 * Interface for search context helper classes to create attribute
 * search iterators based on posting lists and using dictionary
 * information to better estimate number of hits.  Also used for
 * enumerated attributes without posting lists to eliminate brute
 * force searches for nonexisting values.
 */

class IPostingListSearchContext
{
protected:
    IPostingListSearchContext() { }
    virtual ~IPostingListSearchContext() { }

public:
    virtual void fetchPostings(const queryeval::ExecuteInfo & execInfo) = 0;
    virtual std::unique_ptr<queryeval::SearchIterator> createPostingIterator(fef::TermFieldMatchData *matchData, bool strict) = 0;
    virtual unsigned int approximateHits() const = 0;
};

}
