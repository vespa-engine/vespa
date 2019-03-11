// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trace.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace search::engine {

RelativeTime::RelativeTime(std::unique_ptr<Clock> clock)
    : _start(clock->now()),
      _clock(std::move(clock))
{}

Trace::Trace(const RelativeTime & relativeTime, uint32_t level)
    : _trace(std::make_unique<vespalib::Slime>()),
      _root(_trace->setObject()),
      _traces(_root.setArray("traces")),
      _relativeTime(relativeTime),
      _level(level)
{
   _root.setString("start_time_utc", _relativeTime.timeOfDawn().toString());
}

Trace::~Trace() = default;

Trace::Cursor &
Trace::createCursor(vespalib::stringref name) {
    Cursor & trace = _traces.addObject();
    addTimeStamp(trace);
    trace.setString("tag", name);
    return trace;
}

void
Trace::addEvent(uint32_t level, vespalib::stringref event) {
    if (!shouldTrace(level)) return;

    Cursor & trace = _traces.addObject();
    addTimeStamp(trace);
    trace.setString("event", event);
}

void
Trace::addTimeStamp(Cursor & trace) {
    trace.setDouble("timestamp_ms", _relativeTime.timeSinceDawn()/1000000.0);
}

vespalib::string
Trace::toString() const {
    return _trace->toString();
}

}
