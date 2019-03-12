// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trace.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace search::engine {

RelativeTime::RelativeTime(std::unique_ptr<Clock> clock)
    : _start(clock->now()),
      _clock(std::move(clock))
{}

void
Trace::constructObject() {
    _trace = std::make_unique<vespalib::Slime>();
    _root = & _trace->setObject();
}

void
Trace::constructTraces() {
    _traces = &_root->setArray("traces");
}

void
Trace::lazyConstruct(uint32_t level) {
    if (shouldTrace(level) && !_trace) {
        constructObject();
        constructTraces();
    }
}

Trace::Trace(const RelativeTime & relativeTime, uint32_t level)
    : _trace(),
      _root(nullptr),
      _traces(nullptr),
      _relativeTime(relativeTime),
      _level(level)
{
}

void
Trace::start(int level) {
    if (shouldTrace(level) && !_trace) {
        constructObject();
        _root->setString("start_time_utc", _relativeTime.timeOfDawn().toString());
        constructTraces();
    }
}

Trace::~Trace() = default;

Trace::Cursor &
Trace::createCursor(vespalib::stringref name) {
    lazyConstruct(_level);
    Cursor & trace = _traces->addObject();
    addTimeStamp(trace);
    trace.setString("tag", name);
    return trace;
}

Trace::Cursor *
Trace::maybeCreateCursor(uint32_t level, vespalib::stringref name) {
    lazyConstruct(level);
    return shouldTrace(level) ? & createCursor(name) : nullptr;
}

void
Trace::addEvent(uint32_t level, vespalib::stringref event) {
    if (!shouldTrace(level)) { return; }

    lazyConstruct(level);
    Cursor & trace = _traces->addObject();
    addTimeStamp(trace);
    trace.setString("event", event);
}

void
Trace::addTimeStamp(Cursor & trace) {
    trace.setDouble("timestamp_ms", _relativeTime.timeSinceDawn()/1000000.0);
}

void Trace::done() {
    if (!_root) { return; }

    _root->setDouble("duration_ms", _relativeTime.timeSinceDawn()/1000000.0);
}

vespalib::string
Trace::toString() const {
    return _trace ? _trace->toString() : "";
}

}
