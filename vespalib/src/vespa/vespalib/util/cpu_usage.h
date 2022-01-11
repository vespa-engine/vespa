// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spin_lock.h"
#include <vespa/vespalib/util/time.h>
#include <memory>
#include <future>
#include <vector>
#include <map>

namespace vespalib {

namespace cpu_usage {

/**
 * Samples the total CPU usage of the thread that created it. Note
 * that this must not be used after thread termination. Enables
 * sampling the CPU usage of a thread from outside the thread.
 **/
struct ThreadSampler {
    using UP = std::unique_ptr<ThreadSampler>;
    virtual duration sample() const = 0;
    virtual ~ThreadSampler() {}
};

ThreadSampler::UP create_thread_sampler(bool force_mock_impl = false, double expected_load = 0.16);

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
        SETUP = 0,    // usage related to system setup (init/(re-)config/etc.)
        READ = 1,     // usage related to reading data from the system
        WRITE = 2,    // usage related to writing data to the system
        COMPACT = 3,  // usage related to internal data re-structuring
        MAINTAIN = 4, // usage related to distributed cluster maintainance
        NETWORK = 5,  // usage related to network communication
        OTHER = 6     // unspecified usage
    };
    static constexpr size_t index_of(Category cat) { return static_cast<size_t>(cat); }
    static constexpr size_t num_categories = 7;

    // A sample contains how much CPU has been spent in various
    // categories.
    class Sample {
    private:
        std::array<duration,num_categories> _usage;
    public:
        Sample() : _usage() {}
        size_t size() const { return _usage.size(); }
        duration &operator[](size_t idx) { return _usage[idx]; }
        duration &operator[](Category cat) { return _usage[index_of(cat)]; }
        const duration &operator[](size_t idx) const { return _usage[idx]; }
        const duration &operator[](Category cat) const { return _usage[index_of(cat)]; }
        void merge(const Sample &rhs) {
            for (size_t i = 0; i < size(); ++i) {
                _usage[i] += rhs._usage[i];
            }
        }
    };

    // a sample tagged with the time it was taken
    using TimedSample = std::pair<steady_time, Sample>;

    // Used when multiple threads call the 'sample' function at the
    // same time. One thread will sample while the others will wait
    // for the result.
    struct SampleConflict {
        std::promise<TimedSample>       sample_promise;
        std::shared_future<TimedSample> future_sample;
        SampleConflict() : sample_promise(),
                           future_sample(sample_promise.get_future()) {}
    };

    // Interface used to perform destructive sampling of the CPU spent
    // in various categories since the last time it was sampled.
    struct ThreadTracker {
        using SP = std::shared_ptr<ThreadTracker>;
        virtual Sample sample() = 0;
        virtual ~ThreadTracker() {}
    };
    class ThreadTrackerImpl;

    // Used by threads to signal what kind of CPU they are currently
    // using. The thread will contribute to the declared CPU usage
    // category while this object lives. MyUsage instances may shadow
    // each other, but must be destructed in reverse construction
    // order. The preferred way to use this class is by doing:
    //
    // auto my_usage = CpuUsage::use(my_cat);
    class MyUsage {
    private:
        uint32_t _old_cat_idx;
    public:
        MyUsage(Category cat);
        MyUsage(MyUsage &&) = delete;
        MyUsage(const MyUsage &) = delete;
        MyUsage &operator=(MyUsage &&) = delete;
        MyUsage &operator=(const MyUsage &) = delete;
        ~MyUsage();
    };

private:
    SpinLock                                   _lock;
    Sample                                     _usage;
    std::map<ThreadTracker*,ThreadTracker::SP> _threads;
    bool                                       _sampling;
    std::unique_ptr<SampleConflict>            _conflict;
    std::vector<ThreadTracker::SP>             _pending_add;
    std::vector<ThreadTracker::SP>             _pending_remove;

    using Guard = std::lock_guard<SpinLock>;

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
};

} // namespace
