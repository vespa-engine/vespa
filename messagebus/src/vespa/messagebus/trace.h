// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/trace/trace.h>
#include <vespa/messagebus/tracenode.h>

namespace mbus {

    typedef vespalib::Trace Trace;

#define MBUS_TRACE2(ttrace, level, note, addTime) \
    VESPALIB_TRACE2(ttrace, level, note, addTime)

#define MBUS_TRACE(trace, level, note) VESPALIB_TRACE2(trace, level, note, true)

} // namespace mbus

