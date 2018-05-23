// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::attribute {

class ISearchContext;

class ElementIterator : public queryeval::SearchIterator
{
private:
    SearchIterator::UP _search;
    ISearchContext   & _searchContext;

    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override;
    void initRange(uint32_t beginid, uint32_t endid) override;
public:
    ElementIterator(SearchIterator::UP search, ISearchContext & sc);
    ~ElementIterator();
};

}
