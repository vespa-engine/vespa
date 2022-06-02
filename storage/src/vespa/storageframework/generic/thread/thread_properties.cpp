// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread_properties.h"

namespace storage::framework {

ThreadProperties::ThreadProperties(vespalib::duration waitTime,
                                   vespalib::duration maxProcessTime,
                                   int ticksBeforeWait)
    : _maxProcessTime(maxProcessTime),
      _waitTime(waitTime),
      _ticksBeforeWait(ticksBeforeWait)
{
}

}
