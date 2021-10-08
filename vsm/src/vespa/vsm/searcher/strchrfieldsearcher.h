// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"

namespace vsm {

class StrChrFieldSearcher : public FieldSearcher
{
public:
    StrChrFieldSearcher() : FieldSearcher(0) { }
    StrChrFieldSearcher(FieldIdT fId) : FieldSearcher(fId) { }
    void onValue(const document::FieldValue & fv) override;
    void prepare(search::streaming::QueryTermList & qtl, const SharedSearcherBuf & buf) override;
private:
    size_t shortestTerm() const;
    bool matchDoc(const FieldRef & field);
    virtual size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) = 0;
    virtual size_t matchTerms(const FieldRef & f, const size_t shortestTerm) = 0;
};

}
