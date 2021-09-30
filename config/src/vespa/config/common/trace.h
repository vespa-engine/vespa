// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/trace/tracenode.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/memory.h>

namespace vespalib::slime {
    struct Cursor;
    struct Inspector;
}
namespace config {

/**
 * Clock interface for acquiring time.
 */
struct Clock {
    virtual vespalib::system_time currentTime() const = 0;
    virtual ~Clock() {}
};

/**
 * A simple trace interface which can be used to create a serial trace log of events. Each entry is given a timestamp. The trace
 * can be serialized to/constructed from slime. Is not thread safe.
 */
class Trace
{
public:
    Trace(const Trace & other);
    Trace();
    Trace(uint32_t traceLevel);
    Trace(uint32_t traceLevel, const Clock & clock);

    bool shouldTrace(uint32_t level) const;
    void trace(uint32_t level, const vespalib::string & message);

    void serialize(vespalib::slime::Cursor & cursor) const;
    void deserialize(const vespalib::slime::Inspector & inspector);
    const vespalib::TraceNode & getRoot() const { return _root; }

    vespalib::string toString() const;
private:
    void serializeTraceLog(vespalib::slime::Cursor & array) const;
    void deserializeTraceLog(const vespalib::slime::Inspector & inspector);
    static const vespalib::Memory TRACELOG;
    static const vespalib::Memory TRACELEVEL;
    vespalib::TraceNode _root;
    uint32_t _traceLevel;
    const Clock & _clock;
};

} // namespace config

