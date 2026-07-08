// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initializer_task.h"

#include "load_memory_usage.h"

namespace proton::initializer {

InitializerTask::InitializerTask() noexcept : _state(State::BLOCKED), _dependencies() {
}

InitializerTask::~InitializerTask() = default;

void InitializerTask::addDependency(SP dependency) {
    _dependencies.emplace_back(std::move(dependency));
}

LoadMemoryUsage InitializerTask::get_load_memory_usage() const noexcept {
    return LoadMemoryUsage();
}

void InitializerTask::accept_visitor(InitializerTaskVisitor& visitor) {
    for (auto& task : _dependencies) {
        task->accept_visitor(visitor);
    }
}

} // namespace proton::initializer
