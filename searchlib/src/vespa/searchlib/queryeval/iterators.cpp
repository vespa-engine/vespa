// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2002-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".iterators");

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
