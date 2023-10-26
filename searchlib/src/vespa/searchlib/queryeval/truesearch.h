// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::queryeval {

/**
 * Search iterator for testing, yielding a hit on all documents.
 * Unpacks (sets docid) to the given TermFieldMatchData.
 **/
class TrueSearch : public SearchIterator
{
private:
    fef::TermFieldMatchData & _tfmd;
    Trinary is_strict() const override { return Trinary::True; }
    void doSeek(uint32_t) override;
    void doUnpack(uint32_t) override;

public:
    TrueSearch(fef::TermFieldMatchData & tfmd);
    ~TrueSearch();
};

}
