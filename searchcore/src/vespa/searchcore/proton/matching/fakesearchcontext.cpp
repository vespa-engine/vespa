// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fakesearchcontext.h"

namespace proton {
namespace matching {

FakeSearchContext::FakeSearchContext(size_t initialNumDocs)
    : _clock(),
      _doom(_clock, -1),
      _selector(new search::FixedSourceSelector(0, "fs", initialNumDocs)),
      _indexes(new IndexCollection(_selector)),
      _attrSearchable(),
      _docIdLimit(initialNumDocs)
{}

FakeSearchContext::~FakeSearchContext() {}

} // namespace matching
} // namespace proton
