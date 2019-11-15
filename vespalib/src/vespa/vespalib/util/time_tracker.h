// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <vespa/fastos/timestamp.h>

namespace vespalib {

//-----------------------------------------------------------------------------

class TimeTracker
{
private:
    struct Task {
        vespalib::string name;
        fastos::StopWatch task_time;
        std::vector<Task> sub_tasks;
        Task(const char *name_in) : name(name_in), task_time() { }
        ~Task();
        void close_task() { task_time.stop(); }
        double ms() const { return (task_time.elapsed().sec() * 1000.0); }
    };

    std::vector<Task> _tasks;
    uint32_t _current_level;
    uint32_t _max_level;

    static void build_stats_string(const std::vector<Task> &tasks, uint32_t level,
                                   vespalib::string level_name, vespalib::string &out);
    static std::vector<Task> &find_tasks(std::vector<Task> &tasks, uint32_t level);

public:
    explicit TimeTracker(uint32_t max_level_in);
    ~TimeTracker();
    uint32_t level() const { return _current_level; }
    uint32_t max_level() const { return _max_level; }
    void open_task(const char *name);
    void close_task();
    vespalib::string get_stats();
};

//-----------------------------------------------------------------------------

namespace time_tracker {

struct ThreadBinder {
    TimeTracker &tracker;
    ThreadBinder *parent_binder;
    static __thread ThreadBinder *tl_current_binder;
    ThreadBinder(ThreadBinder &&) = delete;
    ThreadBinder(const ThreadBinder &) = delete;    
    explicit ThreadBinder(TimeTracker &tracker_in);
    ~ThreadBinder();
};

void open_task(const char *name) {
    if (__builtin_expect(ThreadBinder::tl_current_binder != nullptr, false)) {
        ThreadBinder::tl_current_binder->tracker.open_task(name);
    }
}

void close_task() {
    if (__builtin_expect(ThreadBinder::tl_current_binder != nullptr, false)) {
        ThreadBinder::tl_current_binder->tracker.close_task();
    }
}

struct Scope {
    Scope(Scope &&) = delete;
    Scope(const Scope &) = delete;
    Scope(const char *name) { open_task(name); }
    ~Scope() { close_task(); }
};

} // namespace vespalib::time_tracker

//-----------------------------------------------------------------------------

#define TIMED_CAT_IMPL(a, b) a ## b
#define TIMED_CAT(a, b) TIMED_CAT_IMPL(a, b)
#define TIMED_THREAD(tracer) ::vespalib::time_tracker::ThreadBinder TIMED_CAT(timed_thread_, __LINE__)(tracer)
#define TIMED_SCOPE(name) ::vespalib::time_tracker::Scope TIMED_CAT(timed_scope_, __LINE__)(name)
#define TIMED(name, code) do { ::vespalib::time_tracker::open_task(name); code; ::vespalib::time_tracker::close_task(); } while(false)

//-----------------------------------------------------------------------------

} // namespace vespalib

