// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fieldsearcher.h"

namespace vsm {

class BoolFieldSearcher : public FieldSearcher
{
public:
    DUPLICATE(BoolFieldSearcher);
    BoolFieldSearcher(FieldIdT fId);
    ~BoolFieldSearcher();
    void prepare(search::QueryTermList & qtl, const SharedSearcherBuf & buf) override;
    void onValue(const document::FieldValue & fv) override;
private:
    std::vector<bool> _terms;
};

}

