// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "push_context.h"

namespace search::memoryindex {

PushContext::PushContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : BundledFieldsContext(id)
{
}

PushContext::~PushContext() = default;

}
