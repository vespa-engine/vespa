// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/elementiterator.h>

namespace search::fef { class TermFieldMatchData; }
namespace search::attribute {

class ISearchContext;

class SearchContextElementIterator : public queryeval::ElementIterator
{
private:
    const ISearchContext    & _searchContext;
public:
    void getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) override;
    void mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) override;
    SearchContextElementIterator(queryeval::SearchIterator::UP search, const ISearchContext & sc);
    ~SearchContextElementIterator() override;
};

}
