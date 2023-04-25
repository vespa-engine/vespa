// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"

namespace search::attribute {

/*
 * EmptySearchContext is used by attribute vectors that don't support search.
 */
class EmptySearchContext : public SearchContext
{
    int32_t onFind(DocId, int32_t, int32_t&) const override;
    int32_t onFind(DocId, int32_t) const override;
    unsigned int approximateHits() const override;
    std::unique_ptr<queryeval::SearchIterator> createIterator(fef::TermFieldMatchData*, bool) override;
    std::unique_ptr<queryeval::SearchIterator> createFilterIterator(fef::TermFieldMatchData*, bool) override;
public:
    EmptySearchContext(const AttributeVector& attr) noexcept;
    ~EmptySearchContext();
    uint32_t get_committed_docid_limit() const noexcept override;
};

}
