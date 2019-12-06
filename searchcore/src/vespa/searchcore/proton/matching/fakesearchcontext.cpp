// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakesearchcontext.h"

namespace proton::matching {

FakeSearchContext::FakeSearchContext(size_t initialNumDocs)
    : _clock(),
      _doom(_clock, vespalib::steady_time()),
      _selector(new search::FixedSourceSelector(0, "fs", initialNumDocs)),
      _indexes(new IndexCollection(_selector)),
      _attrSearchable(),
      _docIdLimit(initialNumDocs)
{
    _attrSearchable.is_attr(true);
}

FakeSearchContext::~FakeSearchContext() = default;

}
