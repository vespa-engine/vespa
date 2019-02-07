// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time_tracer.h"

namespace vespalib::test {

//-----------------------------------------------------------------------------

TimeTracer &
TimeTracer::master()
{
    static TimeTracer instance;
    return instance;
}

//-----------------------------------------------------------------------------

thread_local TimeTracer::ThreadState *TimeTracer::_thread_state = nullptr;

//-----------------------------------------------------------------------------

TimeTracer::Tag::Tag(const vespalib::string &name)
    : _id(master().get_tag_id(name))
{
}

TimeTracer::Tag::~Tag() = default;

//-----------------------------------------------------------------------------

void
TimeTracer::init_thread_state() noexcept
{
    _thread_state = master().create_thread_state();
}

//-----------------------------------------------------------------------------

TimeTracer::TimeTracer()
    : _lock(),
      _state_list(),
      _tags()
{
}

uint32_t
TimeTracer::get_tag_id(const vespalib::string &tag_name)
{
    std::lock_guard guard(_lock);
    auto pos = _tags.find(tag_name);
    if (pos != _tags.end()) {
        return pos->second;
    }
    uint32_t id = _tags.size();
    _tags[tag_name] = id;
    return id;
}

TimeTracer::ThreadState *
TimeTracer::create_thread_state()
{
    std::lock_guard guard(_lock);
    _state_list.push_back(std::make_unique<ThreadState>());
    return _state_list.back().get();
}

std::vector<TimeTracer::Record>
TimeTracer::extract_all_impl()
{
    std::lock_guard guard(_lock);
    std::vector<Record> list;
    for (size_t thread_id = 0; thread_id < _state_list.size(); ++thread_id) {
        const LogEntry *entry = _state_list[thread_id]->get_log_entries();
        while (entry != nullptr) {
            list.emplace_back(thread_id, entry->tag_id, entry->start, entry->stop);
            entry = entry->next;
        }
    }
    return list;
}

TimeTracer::~TimeTracer() = default;

//-----------------------------------------------------------------------------

std::vector<TimeTracer::Record>
TimeTracer::extract_all()
{
    return master().extract_all_impl();
}

//-----------------------------------------------------------------------------

} // namespace vespalib::test
