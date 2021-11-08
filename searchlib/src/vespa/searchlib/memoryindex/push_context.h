// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bundled_fields_context.h"

namespace search::memoryindex {

/*
 * Context for pushing inverted data to memory index structure for a set
 * of fields and uri fields. Currently used by PushTask.
 */
class PushContext : public BundledFieldsContext
{
public:
    PushContext(vespalib::ISequencedTaskExecutor::ExecutorId id);
    ~PushContext();
};

}
