// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <functional>

namespace vespalib {

namespace slime { struct Cursor; }

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
        virtual void report(slime::Cursor &obj, ReportContext &ctx) const = 0;
    };
    using NameMapper = std::function<vespalib::string(const vespalib::string &)>;

private:
    size_t _level;
    size_t _max_depth;
    std::vector<vespalib::string> _names;
    vespalib::hash_map<vespalib::string,size_t> _name_map;
    std::unique_ptr<Impl> _impl;

public:
    ExecutionProfiler(int32_t profile_depth);
    ~ExecutionProfiler();
    TaskId resolve(const vespalib::string &name);
    const vespalib::string &name_of(TaskId task) const { return _names[task]; }
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
    void report(slime::Cursor &obj, const NameMapper &name_mapper =
                [](const vespalib::string &name) noexcept { return name; }) const;
};

}
