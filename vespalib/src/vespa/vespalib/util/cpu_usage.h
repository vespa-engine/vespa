// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "runnable.h"
#include "executor.h"
#include "spin_lock.h"
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/stllike/string.h>
#include <array>
#include <memory>
#include <future>
#include <vector>
#include <map>

namespace vespalib {

namespace cpu_usage {

/**
 * Samples the total CPU usage of this process so far.
 **/
duration total_cpu_usage() noexcept;

/**
 * Samples the total CPU usage of the thread that created it. Note
 * that this must not be used after thread termination. Enables
 * sampling the CPU usage of a thread from outside the thread.
 **/
struct ThreadSampler {
    using UP = std::unique_ptr<ThreadSampler>;
    virtual duration sample() const noexcept = 0;
    virtual ~ThreadSampler() {}
};

ThreadSampler::UP create_thread_sampler(bool force_mock_impl = false, double expected_util = 0.16);

} // cpu_usage

/**
 * Tracks accumulative cpu usage across threads and work
 * categories. Use the 'use' function to signal what kind of CPU you
 * are using in the current thread. Use the 'sample' function to get a
 * complete view of CPU usage so far.
 *
 * The 'use' function returns a MyUsage object that needs to be kept
 * alive for as long as the current thread should contribute to the
 * specified cpu use. Note that MyUsage instances may shadow each
 * other, but must be destructed in reverse construction order.
 **/
class CpuUsage
{
public:
    // The kind of work performed by a thread. Used to separate
    // different kinds of CPU usage. Note that categories are
    // exclusive/non-overlapping; a thread can only perform one kind
    // of CPU work at any specific time.
    enum class Category {
        SETUP   = 0, // usage related to system setup (init/(re-)config/etc.)
        READ    = 1, // usage related to reading data from the system
        WRITE   = 2, // usage related to writing data to the system
        COMPACT = 3, // usage related to internal data re-structuring
        OTHER   = 4  // all other cpu usage not in the categories above
    };
    static vespalib::string &name_of(Category cat);
    static constexpr size_t index_of(Category cat) { return static_cast<size_t>(cat); }
    static constexpr size_t num_categories = 5;

    template <typename T>
    class PerCategory {
    private:
        std::array<T,num_categories> _array;
    public:
        PerCategory() : _array() {}
        size_t size() const { return _array.size(); }
        T &operator[](size_t idx) { return _array[idx]; }
        T &operator[](Category cat) { return _array[index_of(cat)]; }
        const T &operator[](size_t idx) const { return _array[idx]; }
        const T &operator[](Category cat) const { return _array[index_of(cat)]; }
    };

    // A sample contains how much CPU has been spent in each category.
    class Sample : public PerCategory<duration> {
    public:
        void merge(const Sample &rhs) {
            for (size_t i = 0; i < size(); ++i) {
                (*this)[i] += rhs[i];
            }
        }
    };

    // a sample tagged with the time it was taken
    using TimedSample = std::pair<steady_time, Sample>;

    // Used by threads to signal what kind of CPU they are currently
    // using. The thread will contribute to the declared CPU usage
    // category while this object lives. MyUsage instances may shadow
    // each other, but must be destructed in reverse construction
    // order. The preferred way to use this class is by doing:
    //
    // auto my_usage = CpuUsage::use(my_cat);
    class MyUsage {
    private:
        Category _old_cat;
        static Category set_cpu_category_for_this_thread(Category cat) noexcept;
    public:
        MyUsage(Category cat)
          : _old_cat(set_cpu_category_for_this_thread(cat)) {}
        MyUsage(MyUsage &&) = delete;
        MyUsage(const MyUsage &) = delete;
        MyUsage &operator=(MyUsage &&) = delete;
        MyUsage &operator=(const MyUsage &) = delete;
        ~MyUsage() { set_cpu_category_for_this_thread(_old_cat); }
    };

    // grant extra access for testing
    struct Test;

private:
    using Guard = std::lock_guard<SpinLock>;

    // Used when multiple threads call the 'sample' function at the
    // same time. One thread will sample while the others will wait
    // for the result.
    struct SampleConflict {
        std::promise<TimedSample>       sample_promise;
        std::shared_future<TimedSample> future_sample;
        size_t waiters;
        SampleConflict() : sample_promise(),
                           future_sample(sample_promise.get_future()),
                           waiters(0) {}
    };

    // Interface used to perform destructive sampling of the CPU spent
    // in various categories since the last time it was sampled.
    struct ThreadTracker {
        using SP = std::shared_ptr<ThreadTracker>;
        virtual Sample sample() noexcept = 0;
        virtual ~ThreadTracker() {}
    };

    class ThreadTrackerImpl : public ThreadTracker {
    private:
        SpinLock                     _lock;
        Category                     _cat;
        duration                     _old_usage;
        cpu_usage::ThreadSampler::UP _sampler;
        Sample                       _pending;

    public:
        ThreadTrackerImpl(cpu_usage::ThreadSampler::UP sampler);
        // only called by owning thread
        Category set_category(Category new_cat) noexcept;
        Sample sample() noexcept override;
    };

    SpinLock                                   _lock;
    Sample                                     _usage;
    std::map<ThreadTracker*,ThreadTracker::SP> _threads;
    bool                                       _sampling;
    std::unique_ptr<SampleConflict>            _conflict;
    std::vector<ThreadTracker::SP>             _pending_add;
    std::vector<ThreadTracker::SP>             _pending_remove;

    CpuUsage();
    CpuUsage(CpuUsage &&) = delete;
    CpuUsage(const CpuUsage &) = delete;
    CpuUsage &operator=(CpuUsage &&) = delete;
    CpuUsage &operator=(const CpuUsage &) = delete;

    static CpuUsage &self();

    void do_add_thread(const Guard &guard, ThreadTracker::SP tracker);
    void do_remove_thread(const Guard &guard, ThreadTracker::SP tracker);

    void add_thread(ThreadTracker::SP tracker);
    void remove_thread(ThreadTracker::SP tracker);

    void handle_pending(const Guard &guard);
    TimedSample do_sample();
    TimedSample sample_or_wait();

public:
    static MyUsage use(Category cat) { return MyUsage(cat); }
    static TimedSample sample();
    static Runnable::init_fun_t wrap(Runnable::init_fun_t init, Category cat);
    static Executor::Task::UP wrap(Executor::Task::UP task, Category cat);
};

/**
 * Simple class used to track cpu utilization over time.
 **/
class CpuUtil
{
private:
    duration _min_delay;
    CpuUsage::TimedSample _old_sample;
    CpuUsage::PerCategory<double> _util;

public:
    CpuUtil(duration min_delay = 850ms)
      : _min_delay(min_delay),
        _old_sample(CpuUsage::sample()),
        _util() {}

    CpuUsage::PerCategory<double> get_util() {
        if (steady_clock::now() >= (_old_sample.first + _min_delay)) {
            auto new_sample = CpuUsage::sample();
            auto dt = to_s(new_sample.first - _old_sample.first);
            for (size_t i = 0; i < _util.size(); ++i) {
                _util[i] = to_s(new_sample.second[i] - _old_sample.second[i]) / dt;
            }
            _old_sample = new_sample;
        }
        return _util;
    }
};

} // namespace
