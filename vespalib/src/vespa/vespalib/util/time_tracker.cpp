// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time_tracker.h"
#include "stringfmt.h"
#include <cassert>

namespace vespalib {

TimeTracker::Task::~Task() { }

TimeTracker::TimeTracker(uint32_t max_level_in)
    : _tasks(),
      _current_level(0),
      _max_level(max_level_in)
{ }

TimeTracker::~TimeTracker() {
    assert(_current_level == 0);
}

void
TimeTracker::build_stats_string(const std::vector<Task> &tasks, uint32_t level,
                               vespalib::string level_name, vespalib::string &out)
{
    for (auto task: tasks) {
        vespalib::string name = (level == 0)
                                ? task.name
                                : make_string("%s.%s", level_name.c_str(), task.name.c_str());
        out.append(make_string("%*s%s: %g ms\n", (level * 4), "", name.c_str(), task.ms()));
        build_stats_string(task.sub_tasks, (level + 1), name, out);
    }
}

std::vector<TimeTracker::Task> &
TimeTracker::find_tasks(std::vector<Task> &tasks, uint32_t level) {
    if (level == 0) {
        return tasks;
    }
    assert(!tasks.empty());
    return find_tasks(tasks.back().sub_tasks, (level - 1));
}

void
TimeTracker::open_task(const char *name)
{
    if (++_current_level <= _max_level) {
        std::vector<TimeTracker::Task> &tasks = find_tasks(_tasks, (_current_level - 1));
        tasks.emplace_back(name);
    }
}

void
TimeTracker::close_task()
{
    assert(_current_level > 0);
    if (--_current_level < _max_level) {
        std::vector<TimeTracker::Task> &tasks = find_tasks(_tasks, _current_level);
        assert(!tasks.empty());
        tasks.back().close_task();
    }
}

vespalib::string
TimeTracker::get_stats()
{
    vespalib::string out;
    build_stats_string(_tasks, 0, "", out);
    return out;
}

namespace time_tracker {

__thread ThreadBinder *ThreadBinder::tl_current_binder = nullptr;

ThreadBinder::ThreadBinder(TimeTracker &tracker_in)
    : tracker(tracker_in),
      parent_binder(tl_current_binder)
{
    tl_current_binder = (tracker.max_level() > 0)? this : nullptr;
}

ThreadBinder::~ThreadBinder() {
    if (tracker.max_level() > 0) {
        assert(tl_current_binder == this);
    } else {
        assert(tl_current_binder == nullptr);
    }
    tl_current_binder = parent_binder;
}

} // namespace vespalib::time_tracker

} // namespace vespalib
