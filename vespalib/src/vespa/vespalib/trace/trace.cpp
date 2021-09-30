// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/trace/trace.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/time.h>

namespace vespalib {

Trace::Trace(const Trace &rhs)
    : _root(),
      _level(rhs._level)
{
    if (!rhs.isEmpty()) {
        _root = std::make_unique<TraceNode>(rhs.getRoot());
    }
}

bool
Trace::trace(uint32_t level, const string &note, bool addTime)
{
    if (!shouldTrace(level)) {
        return false;
    }
    if (addTime) {
        struct timeval tv;
        gettimeofday(&tv, nullptr);
        ensureRoot().addChild(make_string("[%ld.%06ld] %s", tv.tv_sec, static_cast<long>(tv.tv_usec), note.c_str()));
    } else {
        ensureRoot().addChild(note);
    }
    return true;
}

string
Trace::toString(size_t limit) const {
    return _root ? _root->toString(limit) : "";
}

string
Trace::encode() const {
    return isEmpty() ? "" : getRoot().encode();
}

void
Trace::clear() {
    _level = 0;
    _root.reset();
}

TraceNode &
Trace::ensureRoot() {
    if (!_root) {
        _root = std::make_unique<TraceNode>();
    }
    return *_root;
}

} // namespace vespalib
