// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/trace/trace.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/time.h>

namespace vespalib {

bool
Trace::trace(uint32_t level, const string &note, bool addTime)
{
    if (!shouldTrace(level)) {
        return false;
    }
    if (addTime) {
        struct timeval tv;
        gettimeofday(&tv, nullptr);
        _root.addChild(make_string("[%ld.%06ld] %s", tv.tv_sec, static_cast<long>(tv.tv_usec), note.c_str()));
    } else {
        _root.addChild(note);
    }
    return true;
}

} // namespace vespalib
