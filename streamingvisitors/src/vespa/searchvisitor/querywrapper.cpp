// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querywrapper.h"

using namespace search::streaming;

namespace streaming {

QueryWrapper::QueryWrapper(Query & query)
    : _termList()
{
    query.getLeaves(_termList);
}

QueryWrapper::~QueryWrapper() = default;

} // namespace streaming

