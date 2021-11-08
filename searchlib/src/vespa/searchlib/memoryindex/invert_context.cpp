// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_context.h"

namespace search::memoryindex {

InvertContext::InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : BundledFieldsContext(id),
      _pushers()
{
}

InvertContext::~InvertContext() = default;

void
InvertContext::add_pusher(uint32_t pusher_id)
{
    _pushers.emplace_back(pusher_id);
}

}
