// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#pragma GCC diagnostic ignored "-Wunused-function" // This dirty one is due a suspected bug in gcc 6.2
#include "time_queue.h"

namespace vbench {

namespace {

struct DummyItem {};

} // namespace vbench::<unnamed>

template class TimeQueue<DummyItem>;

} // namespace vbench
#pragma GCC diagnostic pop
