// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_search_context.h>

namespace search {

class AttributeVector;
class QueryTermSimple;

}

namespace search::queryeval { class SearchIterator; }

namespace search::attribute {

class IPostingListSearchContext;

/*
 * SearchContext handles the creation of search iterators for a query term on an attribute vector.
 * This is an abstract class.
 */
class SearchContext : public ISearchContext
{
protected:
    using QueryTermSimpleUP = std::unique_ptr<QueryTermSimple>;
public:
    SearchContext(const SearchContext&) = delete;
    SearchContext(SearchContext&&) noexcept = default;
    SearchContext& operator=(const SearchContext&) = delete;
    SearchContext& operator=(SearchContext&&) noexcept = delete;
    ~SearchContext() override = default;

    unsigned int approximateHits() const override;
    std::unique_ptr<queryeval::SearchIterator> createIterator(fef::TermFieldMatchData* matchData, bool strict) override;
    void fetchPostings(const queryeval::ExecuteInfo& execInfo) override;
    bool valid() const override { return false; }
    Int64Range getAsIntegerTerm() const override { return Int64Range(); }
    DoubleRange getAsDoubleTerm() const override { return DoubleRange(); }

    const QueryTermUCS4* queryTerm() const override {
        return static_cast<const QueryTermUCS4*>(nullptr);
    }
    const vespalib::string& attributeName() const override;

    const AttributeVector& attribute() const { return _attr; }

protected:
    SearchContext(const AttributeVector& attr) noexcept
        : _attr(attr),
          _plsc(nullptr)
    {}

    const AttributeVector&                _attr;
    attribute::IPostingListSearchContext* _plsc;

    /**
     * Creates an attribute search iterator associated with this
     * search context. Postings lists are not used.
     **/
    virtual std::unique_ptr<queryeval::SearchIterator> createFilterIterator(fef::TermFieldMatchData* matchData, bool strict);

    bool getIsFilter() const;
};

}
