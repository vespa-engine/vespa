// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docid_range_scheduler.h"
#include <cassert>

namespace proton::matching {

namespace {

size_t clamped_sub(size_t a, size_t b) { return (b > a) ? 0 : (a - b); }

} // namespace proton::matching::<unnamed>

const std::atomic<size_t> IdleObserver::_always_zero(0);

//-----------------------------------------------------------------------------

PartitionDocidRangeScheduler::PartitionDocidRangeScheduler(size_t num_threads, uint32_t docid_limit)
    : _ranges()
{
    DocidRangeSplitter splitter(DocidRange(1, docid_limit), num_threads);
    for (size_t i = 0; i < num_threads; ++i) {
        _ranges.push_back(splitter.get(i));
    }
}

PartitionDocidRangeScheduler::~PartitionDocidRangeScheduler() = default;

//-----------------------------------------------------------------------------

DocidRange
TaskDocidRangeScheduler::next_task(size_t thread_id)
{
    std::lock_guard<std::mutex> guard(_lock);
    DocidRange work = _splitter.get(_next_task);
    if (_next_task < _num_tasks) {
        ++_next_task;
    }
    _assigned[thread_id] += work.size();
    size_t todo = _unassigned.load(std::memory_order_relaxed);
    _unassigned.store(clamped_sub(todo, work.size()), std::memory_order_relaxed);
    return work;
}

TaskDocidRangeScheduler::TaskDocidRangeScheduler(size_t num_threads, size_t num_tasks, uint32_t docid_limit)
    : _lock(),
      _splitter(DocidRange(1, docid_limit), num_tasks),
      _next_task(0),
      _num_tasks(num_tasks),
      _assigned(num_threads, 0),
      _unassigned(_splitter.full_range().size())
{
}

TaskDocidRangeScheduler::~TaskDocidRangeScheduler() = default;

//-----------------------------------------------------------------------------

size_t
AdaptiveDocidRangeScheduler::take_idle(const Guard &)
{
    size_t thread_id = _idle.back();
    _idle.pop_back();
    _num_idle.store(_idle.size(), std::memory_order_relaxed);
    assert(_workers[thread_id].is_idle);
    return thread_id;
}

void
AdaptiveDocidRangeScheduler::make_idle(const Guard &, size_t thread_id)
{
    assert(!_workers[thread_id].is_idle);
    _workers[thread_id].is_idle = true;
    _idle.push_back(thread_id);
    _num_idle.store(_idle.size(), std::memory_order_relaxed);
}

void
AdaptiveDocidRangeScheduler::donate(const Guard &guard, size_t src_thread, DocidRange range)
{
    size_t dst_thread = take_idle(guard);
    _workers[dst_thread].next_range = range;
    _workers[dst_thread].is_idle = false;
    _workers[dst_thread].condition.notify_one();
    _assigned[src_thread] -= range.size();
    _assigned[dst_thread] += range.size();
}

bool
AdaptiveDocidRangeScheduler::all_work_done(const Guard &) const
{
    // when all threads are idle at the same time there is no more work
    return ((_idle.size() + 1) == _workers.size());
}

DocidRange
AdaptiveDocidRangeScheduler::finalize(const Guard &guard, size_t thread_id)
{
    while (!_idle.empty()) {
        donate(guard, thread_id, DocidRange());
    }
    return DocidRange();
}

AdaptiveDocidRangeScheduler::AdaptiveDocidRangeScheduler(size_t num_threads, uint32_t min_task, uint32_t docid_limit)
    : _splitter(DocidRange(1, docid_limit), num_threads),
      _min_task(std::max(1u, min_task)),
      _lock(),
      _assigned(num_threads, 0),
      _workers(num_threads),
      _idle(),
      _num_idle(_idle.size())
{
    _idle.reserve(num_threads);
    for (size_t i = 0; i < num_threads; ++i) {
        _assigned[i] = _splitter.get(i).size();
    }
}

AdaptiveDocidRangeScheduler::~AdaptiveDocidRangeScheduler() = default;

DocidRange
AdaptiveDocidRangeScheduler::first_range(size_t thread_id)
{
    DocidRange range = _splitter.get(thread_id);
    if (range.empty()) {
        // block and be counted as idle
        return next_range(thread_id);
    }
    return range;
}

DocidRange
AdaptiveDocidRangeScheduler::next_range(size_t thread_id)
{
    Guard guard(_lock);
    if (all_work_done(guard)) {
        return finalize(guard, thread_id);
    }
    make_idle(guard, thread_id);
    while (_workers[thread_id].is_idle) {
        _workers[thread_id].condition.wait(guard);
    }
    return _workers[thread_id].next_range;
}

DocidRange
AdaptiveDocidRangeScheduler::share_range(size_t thread_id, DocidRange todo)
{
    size_t max_parts = (todo.size() / _min_task);
    if (max_parts > 1) {
        Guard guard(_lock);
        size_t parts = std::min(_idle.size() + 1, max_parts);
        if (parts > 1) {
            DocidRangeSplitter splitter(todo, parts);
            for (size_t i = 1; i < parts; ++i) {
                donate(guard, thread_id, splitter.get(i));
            }
            return splitter.get(0);
        }
    }
    return todo;
}

//-----------------------------------------------------------------------------

}
