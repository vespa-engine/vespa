// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterators.h"

namespace search::queryeval {

RankedSearchIteratorBase::
RankedSearchIteratorBase(fef::TermFieldMatchDataArray matchData)
    : SearchIterator(),
      _matchData(std::move(matchData)),
      _needUnpack(1)
{ }

RankedSearchIteratorBase::~RankedSearchIteratorBase() = default;

}
