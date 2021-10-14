// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/searcher/utf8stringfieldsearcherbase.h>

namespace vsm
{

/**
 * This class does suffix utf8 searches.
 **/
class UTF8ExactStringFieldSearcher : public UTF8StringFieldSearcherBase
{
protected:
    virtual size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;
    virtual size_t matchTerms(const FieldRef & f, const size_t shortestTerm) override;

public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    UTF8ExactStringFieldSearcher()             : UTF8StringFieldSearcherBase() { }
    UTF8ExactStringFieldSearcher(FieldIdT fId) : UTF8StringFieldSearcherBase(fId) { }
};

}

