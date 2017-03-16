// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/range.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

namespace fef {
    class TermFieldMatchData;
}
namespace queryeval {
    class SearchIterator;
}

class QueryTermBase;

namespace attribute {

class ISearchContext {
public:
    using UP = std::unique_ptr<ISearchContext>;
    virtual ~ISearchContext() {}

    virtual unsigned int approximateHits() const = 0;

    /**
     * Creates an attribute search iterator associated with this
     * search context.
     *
     * @return attribute search iterator
     *
     * @param matchData the attribute match data used when
     * unpacking data for a hit
     *
     * @param strict whether the iterator should be strict or not
     **/
    virtual std::unique_ptr<queryeval::SearchIterator>
    createIterator(fef::TermFieldMatchData *matchData, bool strict) = 0;

    /*
     * Create temporary posting lists.
     * Should be called before createIterator() is called.
     */
    virtual void fetchPostings(bool strict) = 0;

    virtual bool valid() const = 0;
    virtual Int64Range getAsIntegerTerm() const = 0;
    virtual const QueryTermBase &queryTerm() const = 0;
    virtual const vespalib::string &attributeName() const = 0;

};

}
}
