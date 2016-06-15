// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include "searchiterator.h"

namespace search {
namespace queryeval {

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

} // namespace queryeval
} // namespace search
