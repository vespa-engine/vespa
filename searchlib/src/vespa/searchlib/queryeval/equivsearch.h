// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "orlikesearch.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/termmatchdatamerger.h>

namespace search::queryeval {

/**
 * A simple implementation of the Equiv search operation.
 **/
class EquivSearch : public SearchIterator
{
public:
    using Children = MultiSearch::Children;

    static SearchIterator::UP
    create(Children children,
           fef::MatchData::UP inputMD,
           const fef::TermMatchDataMerger::Inputs &inputs,
           const fef::TermFieldMatchDataArray &outputs,
           bool strict);
};

}
