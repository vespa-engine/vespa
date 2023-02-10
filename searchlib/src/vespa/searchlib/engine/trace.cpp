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

vespalib::slime::Cursor &
LazyTraceInserter::get_entry() {
    if (!_entry) {
        _entry = &_parent.createCursor(_name);
    }
    return *_entry;
}

vespalib::slime::Inserter &
LazyTraceInserter::get_thread_inserter()
{
    if (!_thread_inserter) {
        _thread_inserter = std::make_unique<vespalib::slime::ArrayInserter>(get_entry().setArray("threads"));
    }
    return *_thread_inserter;
}

LazyTraceInserter::LazyTraceInserter(search::engine::Trace &parent, vespalib::StaticStringView name) noexcept
  : _parent(parent), _name(name), _entry(nullptr), _thread_inserter()
{
}

LazyTraceInserter::~LazyTraceInserter() = default;

void
LazyTraceInserter::handle_nested(const search::engine::Trace &nested_trace)
{
    if (nested_trace.hasTrace()) {
        vespalib::slime::ObjectInserter inserter(get_entry(), "traces");
        vespalib::slime::inject(nested_trace.getTraces(), inserter);
    }
}

void
LazyTraceInserter::handle_thread(const search::engine::Trace &thread_trace)
{
    if (thread_trace.hasTrace()) {
        vespalib::slime::inject(thread_trace.getRoot(), get_thread_inserter());
    }
}

Trace::Trace(const Trace &parent, ctor_tag)
    : _trace(),
      _root(nullptr),
      _traces(nullptr),
      _relativeTime(parent._relativeTime),
      _level(parent._level),
      _match_profile_depth(parent._match_profile_depth),
      _first_phase_profile_depth(parent._first_phase_profile_depth),
      _second_phase_profile_depth(parent._second_phase_profile_depth)
{
}

Trace::Trace(const RelativeTime & relativeTime, uint32_t level)
    : _trace(),
      _root(nullptr),
      _traces(nullptr),
      _relativeTime(relativeTime),
      _level(level),
      _match_profile_depth(0),
      _first_phase_profile_depth(0),
      _second_phase_profile_depth(0)
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
