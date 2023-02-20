// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/static_string.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

namespace vespalib { class Slime; }
namespace vespalib::slime { struct Cursor; }
namespace vespalib::slime { struct Inserter; }

namespace search::engine {

class Clock {
public:
    virtual ~Clock() = default;
    virtual vespalib::steady_time now() const = 0;
};

class SteadyClock : public Clock {
public:
    vespalib::steady_time now() const override;
};

class CountingClock : public Clock {
public:
    CountingClock(int64_t start, int64_t increment) : _increment(increment), _nextTime(start) { }
    vespalib::steady_time now() const override {
        int64_t prev = _nextTime;
        _nextTime += _increment;
        return vespalib::steady_time(vespalib::duration(prev));
    }
private:
    const int64_t   _increment;
    mutable int64_t _nextTime;
};

class RelativeTime {
public:
    RelativeTime(std::unique_ptr<Clock> clock);
    vespalib::steady_time timeOfDawn() const { return _start; }
    vespalib::duration timeSinceDawn() const { return _clock->now() - _start; }
    vespalib::steady_time now() const { return _clock->now(); }
private:
    vespalib::steady_time _start;
    std::unique_ptr<Clock>  _clock;
};

class Trace;

// Helper class used to inject subtraces back into a parent trace
class LazyTraceInserter
{
private:
    Trace &_parent;
    vespalib::stringref _name;
    vespalib::slime::Cursor *_entry;
    std::unique_ptr<vespalib::slime::Inserter> _thread_inserter;
    vespalib::slime::Cursor &get_entry();
    vespalib::slime::Inserter &get_thread_inserter();
public:
    LazyTraceInserter(search::engine::Trace &parent, vespalib::StaticStringView name) noexcept;
    ~LazyTraceInserter();
    void handle_nested(const search::engine::Trace &nested_trace);
    void handle_thread(const search::engine::Trace &thread_trace);
};

/**
 * Used for adding traces to a request. Acquire a new Cursor for everytime you want to trace something.
 * Note that it is not thread safe. All use of any cursor aquired must be thread safe.
 */
class Trace
{
private:
    struct ctor_tag {};
public:
    using Cursor = vespalib::slime::Cursor;
    Trace(const Trace &parent, ctor_tag);
    Trace(const RelativeTime & relativeTime, uint32_t traceLevel);
    ~Trace();

    /**
     * Will add start timestamp if level is high enough
     * @param level
     */
    void start(int level, bool useUTC=true);

    /**
     * Will give you a trace entry. It will also add a timestamp relative to the creation of the trace.
     * @param name
     * @return a Cursor to use for further tracing.
     */
    Cursor & createCursor(vespalib::stringref name);
    Cursor * maybeCreateCursor(uint32_t level, vespalib::stringref name);
    /**
     * Will add a simple 'event' string. It will also add a timestamp relative to the creation of the trace.
     * @param level require for actually add the trace.
     * @param event
     */
    void addEvent(uint32_t level, vespalib::stringref event);

    /**
     * Will compute and and a final duration timing.
     */
    void done();

    vespalib::string toString() const;
    bool hasTrace() const { return static_cast<bool>(_trace); }
    Cursor & getRoot() const { return root(); }
    Cursor & getTraces() const { return traces(); }
    vespalib::Slime & getSlime() const { return slime(); }
    bool shouldTrace(uint32_t level) const { return level <= _level; }
    uint32_t getLevel() const { return _level; }
    Trace & setLevel(uint32_t level) { _level = level; return *this; }
    Trace &match_profile_depth(int32_t depth) { _match_profile_depth = depth; return *this; }
    Trace &first_phase_profile_depth(int32_t depth) { _first_phase_profile_depth = depth; return *this; }
    Trace &second_phase_profile_depth(int32_t depth) { _second_phase_profile_depth = depth; return *this; }
    Trace &profile_depth(int32_t depth) {
        match_profile_depth(depth);
        first_phase_profile_depth(depth);
        second_phase_profile_depth(depth);
        return *this;
    }
    int32_t match_profile_depth() const { return _match_profile_depth; }
    int32_t first_phase_profile_depth() const { return _first_phase_profile_depth; }
    int32_t second_phase_profile_depth() const { return _second_phase_profile_depth; }
    Trace make_trace() const { return Trace(*this, ctor_tag()); }
    std::unique_ptr<Trace> make_trace_up() const { return std::make_unique<Trace>(*this, ctor_tag()); }
    LazyTraceInserter make_inserter(vespalib::StaticStringView name) { return {*this, name}; }
private:
    vespalib::Slime & slime() const {
        if (!hasTrace()) {
            constructObject();
        }
        return *_trace;
    }
    Cursor & root() const {
        if (!hasTrace()) {
            constructObject();
        }
        return *_root;
    }
    Cursor & traces() const {
        if (!_traces) {
            constructTraces();
        }
        return *_traces;
    }
    void constructObject() const;
    void constructTraces() const;
    void addTimeStamp(Cursor & trace);
    mutable std::unique_ptr<vespalib::Slime> _trace;
    mutable Cursor      * _root;
    mutable Cursor      * _traces;
    const RelativeTime  & _relativeTime;
    uint32_t              _level;
    int32_t               _match_profile_depth;
    int32_t               _first_phase_profile_depth;
    int32_t               _second_phase_profile_depth;
};

}
