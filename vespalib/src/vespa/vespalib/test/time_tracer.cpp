// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

double
TimeTracer::Record::ms_duration() const
{
    return std::chrono::duration<double, std::milli>(stop - start).count();
}

vespalib::string
TimeTracer::Record::tag_name() const
{
    return master().get_tag_name(tag_id);
}

//-----------------------------------------------------------------------------

bool
TimeTracer::Extractor::keep(const Record &entry) const
{
    return ((!_by_thread || (entry.thread_id == _thread_id)) &&
            (!_by_tag    || (entry.tag_id == _tag_id))       &&
            (!_by_time   || (entry.stop > _a))               &&
            (!_by_time   || (entry.start < _b)));
}

TimeTracer::Extractor &&
TimeTracer::Extractor::by_thread(uint32_t thread_id) &&
{
    _by_thread = true;
    _thread_id = thread_id;
    return std::move(*this);
}

TimeTracer::Extractor &&
TimeTracer::Extractor::by_tag(uint32_t tag_id) &&
{
    _by_tag = true;
    _tag_id = tag_id;
    return std::move(*this);
}

TimeTracer::Extractor &&
TimeTracer::Extractor::by_time(time_point a, time_point b) &&
{
    _by_time = true;
    _a = a;
    _b = b;
    return std::move(*this);
}

std::vector<TimeTracer::Record>
TimeTracer::Extractor::get() const
{
    return master().extract_impl(*this);
}

TimeTracer::Extractor::~Extractor() = default;

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
      _tags(),
      _tag_names()
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
    _tag_names.push_back(tag_name);
    return id;
}

vespalib::string
TimeTracer::get_tag_name(uint32_t tag_id)
{
    std::lock_guard guard(_lock);
    if (tag_id < _tag_names.size()) {
        return _tag_names[tag_id];
    } else {
        return "<undef>";
    }
}

TimeTracer::ThreadState *
TimeTracer::create_thread_state()
{
    std::lock_guard guard(_lock);
    uint32_t thread_id = _state_list.size();
    _state_list.push_back(std::make_unique<ThreadState>(thread_id));
    return _state_list.back().get();
}

std::vector<TimeTracer::Record>
TimeTracer::extract_impl(const Extractor &extractor)
{
    std::lock_guard guard(_lock);
    std::vector<Record> list;
    for (const ThreadState::UP &state: _state_list) {
        const LogEntry *entry = state->get_log_entries();
        while (entry != nullptr) {
            Record record(state->thread_id(), entry->tag_id, entry->start, entry->stop);
            if (extractor.keep(record)) {
                list.push_back(record);
            }
            entry = entry->next;
        }
    }
    return list;
}

TimeTracer::~TimeTracer() = default;

//-----------------------------------------------------------------------------

} // namespace vespalib::test
