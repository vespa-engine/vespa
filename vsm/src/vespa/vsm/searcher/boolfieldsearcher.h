// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"

namespace vsm {

class BoolFieldSearcher : public FieldSearcher
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    BoolFieldSearcher(FieldIdT fId);
    ~BoolFieldSearcher();
    void prepare(search::streaming::QueryTermList & qtl, const SharedSearcherBuf & buf) override;
    void onValue(const document::FieldValue & fv) override;
private:
    std::vector<bool> _terms;
};

}

