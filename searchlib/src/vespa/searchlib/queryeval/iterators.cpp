// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterators.h"

namespace search {

namespace queryeval {

RankedSearchIteratorBase::
RankedSearchIteratorBase(const fef::TermFieldMatchDataArray &matchData)
    : SearchIterator(),
      _matchData(matchData),
      _needUnpack(1)
{ }

} // namespace queryeval

} // namespace search
