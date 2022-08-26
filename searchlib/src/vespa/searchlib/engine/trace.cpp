// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trace.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace search::engine {

vespalib::steady_time
SteadyClock::now() const {
    return vespalib::steady_clock::now();
}

RelativeTime::RelativeTime(std::unique_ptr<Clock> clock)
    : _start(clock->now()),
      _clock(std::move(clock))
{}

void
Trace::constructObject() const {
    _trace = std::make_unique<vespalib::Slime>();
    _root = & _trace->setObject();
}

void
Trace::constructTraces() const {
    _traces = & root().setArray("traces");
}

Trace::Trace(const RelativeTime & relativeTime, uint32_t level, uint32_t profileDepth)
    : _trace(),
      _root(nullptr),
      _traces(nullptr),
      _relativeTime(relativeTime),
      _level(level),
      _profileDepth(profileDepth)
{
}

void
Trace::start(int level, bool useUTC) {
    if (shouldTrace(level) && !hasTrace()) {
        if (useUTC) {
            root().setString("start_time", vespalib::to_string(vespalib::to_utc(_relativeTime.timeOfDawn())));
        } else {
            root().setString("start_time", vespalib::to_string(vespalib::system_time(std::chrono::duration_cast<vespalib::system_time::duration>(_relativeTime.timeOfDawn().time_since_epoch()))));
        }
    }
}

Trace::~Trace() = default;

Trace::Cursor &
Trace::createCursor(vespalib::stringref name) {
    Cursor & trace = traces().addObject();
    addTimeStamp(trace);
    trace.setString("tag", name);
    return trace;
}

Trace::Cursor *
Trace::maybeCreateCursor(uint32_t level, vespalib::stringref name) {
    return shouldTrace(level) ? & createCursor(name) : nullptr;
}

void
Trace::addEvent(uint32_t level, vespalib::stringref event) {
    if (!shouldTrace(level)) { return; }

    Cursor & trace = traces().addObject();
    addTimeStamp(trace);
    trace.setString("event", event);
}

void
Trace::addTimeStamp(Cursor & trace) {
    trace.setDouble("timestamp_ms", vespalib::count_ns(_relativeTime.timeSinceDawn())/1000000.0);
}

void Trace::done() {
    if (!hasTrace()) { return; }

    root().setDouble("duration_ms", vespalib::count_ns(_relativeTime.timeSinceDawn())/1000000.0);
}

vespalib::string
Trace::toString() const {
    return hasTrace() ? slime().toString() : "";
}

}
