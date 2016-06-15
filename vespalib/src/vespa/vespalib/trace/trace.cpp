// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/trace/trace.h>

#include <algorithm>
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/util/vstringfmt.h>

LOG_SETUP(".trace");

namespace vespalib {

Trace::Trace() :
    _level(0),
    _root()
{
    // empty
}

Trace::Trace(uint32_t level) :
    _level(level),
    _root()
{
    // empty
}

Trace &
Trace::clear()
{
    _level = 0;
    _root.clear();
    return *this;
}

Trace &
Trace::swap(Trace &other)
{
    std::swap(_level, other._level);
    _root.swap(other._root);
    return *this;
}

Trace &
Trace::setLevel(uint32_t level)
{
    _level = std::min(level, 9u);
    return *this;
}

bool
Trace::trace(uint32_t level, const string &note, bool addTime)
{
    if (!shouldTrace(level)) {
        return false;
    }
    if (addTime) {
        struct timeval tv;
        gettimeofday(&tv, NULL);
        _root.addChild(vespalib::make_vespa_string(
                "[%ld.%06ld] %s", tv.tv_sec, tv.tv_usec, note.c_str()));
    } else {
        _root.addChild(note);
    }
    return true;
}

} // namespace vespalib
