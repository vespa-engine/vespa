// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "utf8stringfieldsearcherbase.h"

namespace vsm
{

/**
 * This class does suffix utf8 searches.
 **/
class UTF8ExactStringFieldSearcher : public UTF8StringFieldSearcherBase
{
protected:
    size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;
    size_t matchTerms(const FieldRef & f, size_t shortestTerm) override;

public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    explicit UTF8ExactStringFieldSearcher(FieldIdT fId)
        : UTF8StringFieldSearcherBase(fId)
    {
        match_type(EXACT);
    }
};

}

