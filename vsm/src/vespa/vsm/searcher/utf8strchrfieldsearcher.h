// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "utf8stringfieldsearcherbase.h"

namespace vsm {

/**
 * This class does normal utf8 searches.
 * This class uses an highly optimized version of the tokenize method in fastlib.
 **/
class UTF8StrChrFieldSearcher : public UTF8StringFieldSearcherBase
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    UTF8StrChrFieldSearcher()             : UTF8StringFieldSearcherBase() { }
    UTF8StrChrFieldSearcher(FieldIdT fId) : UTF8StringFieldSearcherBase(fId) { }

protected:
    size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;
    size_t matchTerms(const FieldRef & f, const size_t shortestTerm) override;
};

}

