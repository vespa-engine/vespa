// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"

#include <vespa/vespalib/stllike/hash_map.h>

#include <functional>
#include <string>

namespace vespalib {

namespace slime {
struct Cursor;
}

/**
 * A simple single-threaded profiler used to measure where time is
 * spent when executing tasks that may depend on each other (doing one
 * task includes doing another task; like one function calls another
 * function). Each task is identified by a unique name. Data is
 * collected in real-time using signals about when a task is started
 * and when it completes. Any sub-task must complete before any parent
 * task. Any task may be executed any number of times and may depend
 * on any other task.
 **/
class ExecutionProfiler {
public:
    using TaskId = uint32_t;
    struct ReportContext;
    struct Impl {
        virtual ~Impl() = default;
        virtual void track_start(TaskId task) = 0;
        virtual void track_complete() = 0;
        virtual void report(slime::Cursor& obj, ReportContext& ctx) const = 0;
    };
    using NameMapper = std::function<std::string(const std::string&)>;

private:
    size_t                                  _level;
    size_t                                  _max_depth;
    std::vector<std::string>                _names;
    vespalib::hash_map<std::string, size_t> _name_map;
    std::unique_ptr<Impl>                   _impl;

public:
    ExecutionProfiler(int32_t profile_depth);
    ~ExecutionProfiler();
    TaskId resolve(const std::string& name);
    const std::string& name_of(TaskId task) const { return _names[task]; }
    void start(TaskId task) {
        if (++_level <= _max_depth) {
            _impl->track_start(task);
        }
    }
    void complete() {
        if (--_level < _max_depth) {
            _impl->track_complete();
        }
    }
    void report(
        slime::Cursor&    obj,
        const NameMapper& name_mapper = [](const std::string& name) noexcept { return name; }) const;
};

/**
 * RAII helper that brackets a single task on an ExecutionProfiler.
 * Hot-path form: takes a pre-resolved TaskId. Use when the same task is
 * entered many times and the cost of resolving names per entry matters.
 **/
struct ProfilerTaskGuard {
    ExecutionProfiler& profiler;
    ProfilerTaskGuard(ExecutionProfiler& profiler_in, ExecutionProfiler::TaskId task) noexcept
        : profiler(profiler_in) { profiler.start(task); }
    ProfilerTaskGuard(const ProfilerTaskGuard&) = delete;
    ProfilerTaskGuard& operator=(const ProfilerTaskGuard&) = delete;
    ~ProfilerTaskGuard() { profiler.complete(); }
};

/**
 * RAII helper that brackets a single task on an ExecutionProfiler.
 * Cold-path form: takes a nullable profiler and a name-producing callable.
 * The name is built and resolved only when the profiler is non-null, so
 * callers do not need to branch around profiling being disabled.
 **/
class ProfilerNameGuard {
    ExecutionProfiler* _profiler;
public:
    ProfilerNameGuard(ExecutionProfiler* profiler_in, auto&& name_fn) noexcept
        : _profiler(profiler_in)
    {
        if (_profiler != nullptr) {
            _profiler->start(_profiler->resolve(name_fn()));
        }
    }
    ProfilerNameGuard(const ProfilerNameGuard&) = delete;
    ProfilerNameGuard& operator=(const ProfilerNameGuard&) = delete;
    ~ProfilerNameGuard() {
        if (_profiler != nullptr) {
            _profiler->complete();
        }
    }
};

} // namespace vespalib
