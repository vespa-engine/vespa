// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "trace.h"
#include <vespa/vespalib/trace/slime_trace_serializer.h>
#include <vespa/vespalib/trace/slime_trace_deserializer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>

using namespace vespalib;
using namespace vespalib::slime;

namespace config {

struct SystemClock : public Clock
{
    vespalib::system_time currentTime() const override {
        return vespalib::system_clock::now();
    }
};

static SystemClock systemClock;

const Memory Trace::TRACELOG("traceLog");
const Memory Trace::TRACELEVEL("traceLevel");

Trace::Trace(const Trace & other)
    : _root(other._root),
      _traceLevel(other._traceLevel),
      _clock(other._clock)
{
}

Trace::Trace()
    : _root(),
      _traceLevel(0),
      _clock(systemClock)
{
}


Trace::Trace(uint32_t traceLevel)
    : _traceLevel(traceLevel),
      _clock(systemClock)
{
}


Trace::Trace(uint32_t traceLevel, const Clock & clock)
    : _traceLevel(traceLevel),
      _clock(clock)
{
}

void
Trace::deserialize(const Inspector & inspector)
{
    _traceLevel = inspector[TRACELEVEL].asLong();
    deserializeTraceLog(inspector[TRACELOG]);
}

void
Trace::deserializeTraceLog(const Inspector & root)
{
    SlimeTraceDeserializer deserializer(root);
    _root = deserializer.deserialize();
}

bool
Trace::shouldTrace(uint32_t level) const
{
    return (level <= _traceLevel);
}

void
Trace::trace(uint32_t level, const vespalib::string & message)
{
    if (shouldTrace(level)) {
        _root.addChild(message, _clock.currentTime());
    }
}

void
Trace::serialize(Cursor & cursor) const
{
    cursor.setLong(TRACELEVEL, _traceLevel);
    SlimeTraceSerializer serializer(cursor.setObject(TRACELOG));
    _root.accept(serializer);
}

void
Trace::serializeTraceLog(Cursor & array) const
{
    for (uint32_t i(0); i < _root.getNumChildren(); i++) {
        SlimeTraceSerializer serializer(array.addObject());
        _root.getChild(i).accept(serializer);
    }
}

vespalib::string
Trace::toString() const
{
    Slime slime;
    serializeTraceLog(slime.setArray());
    SimpleBuffer buf;
    JsonFormat::encode(slime.get(), buf, false);
    return buf.get().make_string();
}


} // namespace config
