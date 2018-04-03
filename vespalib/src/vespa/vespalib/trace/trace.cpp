// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/trace/trace.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/time.h>

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

Trace::~Trace() = default;

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
        _root.addChild(make_string("[%ld.%06ld] %s", tv.tv_sec, tv.tv_usec, note.c_str()));
    } else {
        _root.addChild(note);
    }
    return true;
}

} // namespace vespalib
