// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/searcher/utf8stringfieldsearcherbase.h>

namespace vsm
{

/**
 * This class does suffix utf8 searches.
 **/
class UTF8SuffixStringFieldSearcher : public UTF8StringFieldSearcherBase
{
protected:
    virtual size_t matchTerm(const FieldRef & f, search::QueryTerm & qt) override;
    virtual size_t matchTerms(const FieldRef & f, const size_t shortestTerm) override;

public:
    DUPLICATE(UTF8SuffixStringFieldSearcher);
    UTF8SuffixStringFieldSearcher()             : UTF8StringFieldSearcherBase() { }
    UTF8SuffixStringFieldSearcher(FieldIdT fId) : UTF8StringFieldSearcherBase(fId) { }
};

}

