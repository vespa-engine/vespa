// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace search::queryeval {

class DocIdAndFeatures;

class RankedSearchIteratorBase : public SearchIterator
{
public:
    fef::TermFieldMatchDataArray _matchData;
private:
    uint32_t _needUnpack;
protected:
    void setUnpacked()             { _needUnpack = 0; }
    void clearUnpacked()           { _needUnpack = 1; }
    uint32_t getNeedUnpack() const { return _needUnpack; }
    void incNeedUnpack()           { ++_needUnpack; }
public:
    RankedSearchIteratorBase(const fef::TermFieldMatchDataArray &matchData);
    ~RankedSearchIteratorBase() override;
    bool getUnpacked()       const { return _needUnpack == 0; }
};

}
