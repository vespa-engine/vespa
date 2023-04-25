// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/range.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::fef { class TermFieldMatchData; }
namespace search::queryeval {
    class SearchIterator;
    class ExecuteInfo;
}
namespace search { class QueryTermUCS4; }

namespace search::attribute {

class ISearchContext {
public:
    using UP = std::unique_ptr<ISearchContext>;
    using DocId = uint32_t;

private:
    virtual int32_t onFind(DocId docId, int32_t elementId, int32_t &weight) const = 0;
    virtual int32_t onFind(DocId docId, int32_t elementId) const = 0;

public:
    virtual ~ISearchContext() = default;

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
    virtual void fetchPostings(const queryeval::ExecuteInfo &execInfo) = 0;

    virtual bool valid() const = 0;
    virtual Int64Range getAsIntegerTerm() const = 0;
    virtual DoubleRange getAsDoubleTerm() const = 0;
    virtual const QueryTermUCS4 * queryTerm() const = 0;
    virtual const vespalib::string &attributeName() const = 0;

    int32_t find(DocId docId, int32_t elementId, int32_t &weight) const { return onFind(docId, elementId, weight); }
    int32_t find(DocId docId, int32_t elementId) const { return onFind(docId, elementId); }
    template<typename SC>
    static bool matches(const SC & sc, DocId docId, int32_t &weight) {
        weight = 0;
        int32_t oneWeight(0);
        int32_t firstId = sc.find(docId, 0, oneWeight);
        for (int32_t id(firstId); id >= 0; id = sc.find(docId, id + 1, oneWeight)) {
            weight += oneWeight;
        }
        return firstId >= 0;
    }
    bool matches(DocId docId, int32_t &weight) const { return matches(*this, docId, weight); }
    bool matches(DocId doc) const { return find(doc, 0) >= 0; }

    /*
     * Committed docid limit on attribute vector when search context was
     * created.
     */
    virtual uint32_t get_committed_docid_limit() const noexcept = 0;
};

}
