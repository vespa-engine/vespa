// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/juniper/query.h>
#include <memory>

namespace search {
    class QueryStackIterator;
    class QueryNormalization;
}
namespace search::fef { class Properties; }

namespace search::docsummary {

class IQueryTermFilter;

/*
 * Class implementing an adapter used by juniper to examine the current
 * query.
 */
class JuniperQueryAdapter : public juniper::IQuery
{
private:
    const QueryNormalization * _query_normalization;
    const IQueryTermFilter *_query_term_filter;
    std::unique_ptr<search::QueryStackIterator> _iterator;
    const search::fef::Properties & _highlightTerms;

public:
    JuniperQueryAdapter(const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter operator= (const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter(const QueryNormalization * normalization, const IQueryTermFilter *query_term_filter, std::unique_ptr<search::QueryStackIterator> iterator,
                        const search::fef::Properties & highlightTerms);
    ~JuniperQueryAdapter() override;
    bool skipItem(search::QueryStackIterator& iterator) const;
    bool Traverse(juniper::IQueryVisitor *v) const override;
    bool UsefulIndex(const juniper::QueryItem* item) const override;
};

}
