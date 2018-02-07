// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/begin_and_end_id.h>
#include <vespa/fastos/dynamiclibrary.h>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <algorithm>
#include <vector>

namespace proton::matching {

/**
 * A range of document ids representing a subset of the search space.
 **/
struct DocidRange {
    uint32_t begin;
    uint32_t end;
    DocidRange() : begin(search::endDocId), end(search::endDocId) {}
    DocidRange(uint32_t begin_in, uint32_t end_in)
        : begin(begin_in), end(std::max(begin_in, end_in)) {}
    bool empty() const { return (end <= begin); }
    size_t size() const { return (end - begin); }
};

/**
 * Utility used to split a docid range into multiple consecutive
 * pieces of equal size.
 **/
class DocidRangeSplitter {
private:
    DocidRange _range;
    uint32_t   _step;
    uint32_t   _skew;

    uint32_t offset(uint32_t i) const {
        return std::min(_range.end, _range.begin + (_step * i) + std::min(i, _skew));
    }

public:
    DocidRangeSplitter(DocidRange total_range, size_t count)
        : _range(total_range),
          _step(_range.size() / count),
          _skew(_range.size() % count) {}
    DocidRange get(size_t i) const { return DocidRange(offset(i), offset(i + 1)); }
    DocidRange full_range() const { return _range; }
};

/**
 * Utility class used to poll the current number of idle worker
 * threads as cheaply as possible.
 **/
class IdleObserver {
private:
    static const std::atomic<size_t> _always_zero;
    const std::atomic<size_t> &_num_idle;
public:
    IdleObserver() : _num_idle(_always_zero) {}
    IdleObserver(const std::atomic<size_t> &num_idle) : _num_idle(num_idle) {}
    bool is_always_zero() const { return (&_num_idle == &_always_zero); }
    size_t get() const { return _num_idle.load(std::memory_order::memory_order_relaxed); }
};

/**
 * Interface for the component responsible for assigning docid ranges
 * to search threads during multi-threaded query execution. Each
 * worker starts by calling the 'first_range' function to get
 * something to do. When a worker is ready for more work, it calls the
 * 'next_range' function. When a worker is assigned an empty range,
 * its work is done.
 * 
 * The 'total_span' function returns a range that is guaranteed to
 * contain all ranges assigned to the given worker. The 'total_size'
 * function returns the accumulated size of all ranges assigned to the
 * given worker. The 'unassigned_size' function returns the
 * accumulated size of all currently unassigned ranges.
 *
 * Note that the return values from 'total_span', 'total_size' and
 * 'unassigned_size' may or may not account for the range returned
 * from 'first_range' since the scheduler is allowed to pre-assign
 * ranges to workers. Calling 'first_range' first ensures that all
 * other return values make sense.
 *
 * The 'idle_observer' and 'share_range' functions are used for
 * work-sharing, where a worker thread potentially can offload some of
 * its remaining work to another idle worker thread. The
 * 'idle_observer' function is used to obtain an object that makes it
 * cheap to check whether other threads may be idle. The IdleObserver
 * object will also indicate whether the scheduler implementation
 * supports work-sharing at all. This enables the inner loop to be
 * further specialized by not trying to share work if the scheduler
 * does not support it. The 'share_range' function may be called by
 * any worker thread to try to share some of its remaining work with
 * other idle worker threads. Note that workers that employ
 * work-sharing must support processing docid ranges in non-increasing
 * order. The 'share_range' function should be called when the
 * IdleObserver indicates that other threads may be idle. All
 * remaining work currently assigned to the worker thread is passed
 * into the 'share_range' function. If no other threads are available,
 * the entire range will be returned back out again. If some work
 * could be re-assigned to other threads, the 'share_range' function
 * will return the remaining work to be done by the thread calling
 * it. The returned range is guaranteed to be a prefix of the range
 * passed as input to the 'share_range' function.
 **/
struct DocidRangeScheduler {
    typedef std::unique_ptr<DocidRangeScheduler> UP;
    virtual DocidRange first_range(size_t thread_id) = 0;
    virtual DocidRange next_range(size_t thread_id) = 0;
    virtual DocidRange total_span(size_t thread_id) const = 0;
    virtual size_t total_size(size_t thread_id) const = 0;
    virtual size_t unassigned_size() const = 0;
    virtual IdleObserver make_idle_observer() const = 0;
    virtual DocidRange share_range(size_t thread_id, DocidRange todo) = 0;
    virtual ~DocidRangeScheduler() {}
};

/**
 * A scheduler dividing the total docid space into a single docid
 * range (partition) for each thread. The first thread gets the first
 * part and so on.
 **/
class PartitionDocidRangeScheduler : public DocidRangeScheduler
{
private:
    std::vector<DocidRange> _ranges;
public:
    PartitionDocidRangeScheduler(size_t num_threads, uint32_t docid_limit);
    DocidRange first_range(size_t thread_id) override { return _ranges[thread_id]; }
    DocidRange next_range(size_t) override { return DocidRange(); }
    DocidRange total_span(size_t thread_id) const override { return _ranges[thread_id]; }
    size_t total_size(size_t thread_id) const override { return _ranges[thread_id].size(); }
    size_t unassigned_size() const override { return 0; }
    IdleObserver make_idle_observer() const override { return IdleObserver(); }
    DocidRange share_range(size_t, DocidRange todo) override { return todo; }
};

/**
 * A scheduler dividing the total docid space into tasks of equal
 * size. Tasks are assigned according to increasing docid to the first
 * worker thread that wants more to do.
 **/
class TaskDocidRangeScheduler : public DocidRangeScheduler
{
private:
    std::mutex          _lock;
    DocidRangeSplitter  _splitter;
    size_t              _next_task;
    size_t              _num_tasks;
    std::vector<size_t> _assigned;
    std::atomic<size_t> _unassigned;

    DocidRange next_task(size_t thread_id);
public:
    TaskDocidRangeScheduler(size_t num_threads, size_t num_tasks, uint32_t docid_limit);
    DocidRange first_range(size_t thread_id) override { return next_task(thread_id); }
    DocidRange next_range(size_t thread_id) override { return next_task(thread_id); }
    DocidRange total_span(size_t) const override { return _splitter.full_range(); }
    size_t total_size(size_t thread_id) const override { return _assigned[thread_id]; }
    size_t unassigned_size() const override { return _unassigned.load(std::memory_order::memory_order_relaxed); }
    IdleObserver make_idle_observer() const override { return IdleObserver(); }
    DocidRange share_range(size_t, DocidRange todo) override { return todo; }
};

/**
 * An adaptive scheduler that begins by giving each thread an equal
 * part of the docid space and then uses cooperative work-sharing to
 * re-distribute work between threads as needed.
 **/
class AdaptiveDocidRangeScheduler : public DocidRangeScheduler
{
private:
    using Guard = std::unique_lock<std::mutex>;
    struct Worker {
        std::condition_variable condition;
        bool                    is_idle;
        DocidRange              next_range;
        Worker() : condition(), is_idle(false), next_range() {}
    };
    DocidRangeSplitter  _splitter;
    uint32_t            _min_task;
    std::mutex          _lock;
    std::vector<size_t> _assigned;
    std::vector<Worker> _workers;
    std::vector<size_t> _idle;
    std::atomic<size_t> _num_idle;

    VESPA_DLL_LOCAL size_t take_idle(const Guard &guard);
    VESPA_DLL_LOCAL void make_idle(const Guard &guard, size_t thread_id);
    VESPA_DLL_LOCAL void donate(const Guard &guard, size_t src_thread, DocidRange range);
    VESPA_DLL_LOCAL bool all_work_done(const Guard &guard) const;
    VESPA_DLL_LOCAL DocidRange finalize(const Guard &guard, size_t thread_id);
public:
    AdaptiveDocidRangeScheduler(size_t num_threads, uint32_t min_task, uint32_t docid_limit);
    ~AdaptiveDocidRangeScheduler();
    DocidRange first_range(size_t thread_id) override;
    DocidRange next_range(size_t thread_id) override;
    DocidRange total_span(size_t) const override { return _splitter.full_range(); }
    size_t total_size(size_t thread_id) const override { return _assigned[thread_id]; }
    size_t unassigned_size() const override { return 0; }
    IdleObserver make_idle_observer() const override { return IdleObserver(_num_idle); }
    DocidRange share_range(size_t, DocidRange todo) override;
};

}
