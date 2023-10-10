// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initializer_task.h"

namespace proton::initializer {

InitializerTask::InitializerTask()
    : _state(State::BLOCKED),
      _dependencies()
{
}

InitializerTask::~InitializerTask() = default;

void
InitializerTask::addDependency(SP dependency)
{
    _dependencies.emplace_back(std::move(dependency));
}

size_t
InitializerTask::get_transient_memory_usage() const
{
    return 0u;
}

}
