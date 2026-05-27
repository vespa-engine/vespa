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
    // set_name will only have effect on unnamed tasks
    void set_name(TaskId task, const std::string& name);
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

    // Used to bind an ExecutionProfiler to the current
    // thread. Binding nullptr will do absolutely nothing. Will revert
    // back to the previously bound profiler when destructed.
    class ThreadBinder {
    private:
        ExecutionProfiler* _old = nullptr;
        bool               _active = false;
        ThreadBinder(ExecutionProfiler* profiler) noexcept;
        ThreadBinder(ThreadBinder&&) = delete;
        ThreadBinder(const ThreadBinder&) = delete;
        ThreadBinder& operator=(ThreadBinder&&) = delete;
        ThreadBinder& operator=(const ThreadBinder&) = delete;

    public:
        static ThreadBinder bind(ExecutionProfiler* profiler) noexcept { return ThreadBinder(profiler); }
        static ExecutionProfiler* current() noexcept;
        ~ThreadBinder();
    };

    /**
     * RAII helper that brackets a single task on an
     * ExecutionProfiler. Hot-path form: takes a pre-resolved
     * TaskId. Use when the same task is entered many times and the
     * cost of resolving names per entry matters.
     **/
    class TaskGuard {
    private:
        ExecutionProfiler& _profiler;

    public:
        TaskGuard(ExecutionProfiler& profiler, TaskId task) noexcept : _profiler(profiler) { profiler.start(task); }
        TaskGuard(TaskGuard&&) = delete;
        TaskGuard(const TaskGuard&) = delete;
        TaskGuard& operator=(TaskGuard&&) = delete;
        TaskGuard& operator=(const TaskGuard&) = delete;
        ~TaskGuard() { _profiler.complete(); }
    };

    /**
     * RAII helper that brackets a single task on an
     * ExecutionProfiler. Cold-path form: uses the profiler bound to
     * the current thread (if any). The name is built and resolved
     * only when the profiler is non-null.
     **/
    class NameGuard {
    private:
        ExecutionProfiler* _profiler;
        TaskId             _task{};

    protected:
        ExecutionProfiler* profiler() const noexcept { return _profiler; }
        TaskId task_id() const noexcept { return _task; }

    public:
        explicit NameGuard(auto&& name_fn) : _profiler(ThreadBinder::current()) {
            if (_profiler != nullptr) {
                _task = _profiler->resolve(name_fn());
                _profiler->start(_task);
            }
        }
        NameGuard(NameGuard&& rhs) = delete;
        NameGuard(const NameGuard&) = delete;
        NameGuard& operator=(NameGuard&&) = delete;
        NameGuard& operator=(const NameGuard&) = delete;
        ~NameGuard() {
            if (_profiler != nullptr) {
                _profiler->complete();
            }
        }
    };

    /**
     * RAII helper that brackets a single task on an
     * ExecutionProfiler. Cold-path form: uses the profiler bound to
     * the current thread (if any). The name is given later via the
     * set_name function. The name is built and resolved only when the
     * profiler is non-null.
     **/
    struct PostNameGuard : NameGuard {
        // start out unnamed
        PostNameGuard() : NameGuard([] { return ""; }) {}
        void set_name(auto&& name_fn) {
            if (profiler() != nullptr) {
                profiler()->set_name(task_id(), name_fn());
            }
        }
    };
};

} // namespace vespalib
