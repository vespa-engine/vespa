// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "orlikesearch.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termmatchdatamerger.h>

namespace search {
namespace queryeval {

/**
 * A simple implementation of the Equiv search operation.
 **/
class EquivSearch : public SearchIterator
{
public:
    typedef MultiSearch::Children Children;

    // Caller takes ownership of the returned SearchIterator.
    static SearchIterator *create(const Children &children,
                                  fef::MatchData::UP inputMD,
                                  const search::fef::TermMatchDataMerger::Inputs &inputs,
                                  const search::fef::TermFieldMatchDataArray &outputs,
                                  bool strict);
};

} // namespace queryeval
} // namespace search

