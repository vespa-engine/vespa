// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trace.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace search::engine {

RelativeTime::RelativeTime(std::unique_ptr<Clock> clock)
    : _start(clock->now()),
      _clock(std::move(clock))
{}

Trace::Trace(const RelativeTime & relativeTime)
    : _trace(std::make_unique<vespalib::Slime>()),
      _root(_trace->setObject()),
      _traces(_root.setArray("traces")),
      _relativeTime(relativeTime)
{
   _root.setLong("creation_time", _relativeTime.timeOfDawn());
}

Trace::~Trace() = default;

Trace::Cursor &
Trace::createCursor(vespalib::stringref name) {
    Cursor & trace = _traces.addObject();
    trace.setString("tag", name);
    trace.setLong("time", _relativeTime.timeSinceDawn());
    return trace;
}

vespalib::string
Trace::toString() const {
    return _trace->toString();
}

}
