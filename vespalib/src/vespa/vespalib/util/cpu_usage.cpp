// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cpu_usage.h"
#include "require.h"
#include <pthread.h>
#include <optional>
#include <cassert>

namespace vespalib {

namespace cpu_usage {

namespace {

class DummyThreadSampler : public ThreadSampler {
private:
    steady_time _start;
    double _load;
public:
    DummyThreadSampler(double load) : _start(steady_clock::now()), _load(load) {}
    duration sample() const override {
        return from_s(to_s(steady_clock::now() - _start) * _load);
    }
};

#ifdef __linux__

class LinuxThreadSampler : public ThreadSampler {
private:
    clockid_t _my_clock;
public:
    LinuxThreadSampler() : _my_clock() {
        REQUIRE_EQ(pthread_getcpuclockid(pthread_self(), &_my_clock), 0);
    }
    duration sample() const override {
        timespec ts;
        REQUIRE_EQ(clock_gettime(_my_clock, &ts), 0);
        return from_timespec(ts);
    }
};

#endif

} // <unnamed>

ThreadSampler::UP create_thread_sampler(bool force_mock_impl, double expected_load) {
    if (force_mock_impl) {
        return std::make_unique<DummyThreadSampler>(expected_load);
    }
#ifdef __linux__
    return std::make_unique<LinuxThreadSampler>();
#endif
    return std::make_unique<DummyThreadSampler>(expected_load);
}

} // cpu_usage

class CpuUsage::ThreadTrackerImpl : public CpuUsage::ThreadTracker {
private:
    SpinLock                     _lock;
    uint32_t                     _cat_idx;
    duration                     _old_usage;
    cpu_usage::ThreadSampler::UP _sampler;
    Sample                       _pending;

    using Guard = std::lock_guard<SpinLock>;

    struct Wrapper {
        std::shared_ptr<ThreadTrackerImpl> self;
        Wrapper() : self(std::make_shared<ThreadTrackerImpl>()) {
            CpuUsage::self().add_thread(self);
        }
        ~Wrapper() {
            self->set_category(CpuUsage::num_categories);
            CpuUsage::self().remove_thread(std::move(self));
        }
    };

public:
    ThreadTrackerImpl()
      : _lock(),
        _cat_idx(num_categories),
        _old_usage(),
        _sampler(cpu_usage::create_thread_sampler()),
        _pending()
    {
    }

    uint32_t set_category(uint32_t new_cat_idx) {
        Guard guard(_lock);
        duration new_usage = _sampler->sample();
        if (_cat_idx < num_categories) {
            _pending[_cat_idx] += (new_usage - _old_usage);
        }
        _old_usage = new_usage;
        size_t old_cat_idx = _cat_idx;
        _cat_idx = new_cat_idx;
        return old_cat_idx;
    }

    Sample sample() override {
        Guard guard(_lock);
        if (_cat_idx < num_categories) {
            duration new_usage = _sampler->sample();
            _pending[_cat_idx] += (new_usage - _old_usage);
            _old_usage = new_usage;
        }
        Sample sample = _pending;
        _pending = Sample();
        return sample;
    }

    static ThreadTrackerImpl &self() {
        thread_local Wrapper wrapper;
        return *wrapper.self;
    }
};

CpuUsage::MyUsage::MyUsage(Category cat)
  : _old_cat_idx(ThreadTrackerImpl::self().set_category(index_of(cat)))
{
}

CpuUsage::MyUsage::~MyUsage()
{
    ThreadTrackerImpl::self().set_category(_old_cat_idx);
}

CpuUsage::CpuUsage()
  : _lock(),
    _usage(),
    _threads(),
    _sampling(false),
    _conflict(),
    _pending_add(),
    _pending_remove()
{
}

CpuUsage &
CpuUsage::self()
{
    static CpuUsage me;
    return me;
}

void
CpuUsage::do_add_thread(const Guard &, ThreadTracker::SP tracker)
{
    assert(!_sampling);
    auto *key = tracker.get();
    auto [ignore, was_inserted] = _threads.emplace(key, std::move(tracker));
    assert(was_inserted);
}

void
CpuUsage::do_remove_thread(const Guard &, ThreadTracker::SP tracker)
{
    assert(!_sampling);
    _usage.merge(tracker->sample());
    auto was_removed = _threads.erase(tracker.get());
    assert(was_removed);
}

void
CpuUsage::add_thread(ThreadTracker::SP tracker)
{
    Guard guard(_lock);
    if (_sampling) {
        _pending_add.push_back(std::move(tracker));
    } else {
        do_add_thread(guard, std::move(tracker));
    }
}

void
CpuUsage::remove_thread(ThreadTracker::SP tracker)
{
    Guard guard(_lock);
    if (_sampling) {
        _pending_remove.push_back(std::move(tracker));
    } else {
        do_remove_thread(guard, std::move(tracker));
    }
}

void
CpuUsage::handle_pending(const Guard &guard)
{
    for (auto &thread: _pending_add) {
        do_add_thread(guard, std::move(thread));
    }
    _pending_add.clear();
    for (auto &thread: _pending_remove) {
        do_remove_thread(guard, std::move(thread));
    }
    _pending_remove.clear();
}

CpuUsage::TimedSample
CpuUsage::do_sample()
{
    assert(_sampling);
    Sample my_sample;
    std::optional<std::promise<TimedSample>> my_promise;
    auto t = steady_clock::now();
    for (const auto &entry: _threads) {
        my_sample.merge(entry.first->sample());
    }
    {
        Guard guard(_lock);
        _sampling = false;
        handle_pending(guard);
        if (_conflict) {
            my_promise = std::move(_conflict->sample_promise);
            _conflict.reset();
        }
        my_sample.merge(_usage);
        _usage = my_sample;
    }
    TimedSample result{t, my_sample};
    if (my_promise.has_value()) {
        my_promise.value().set_value(result);
    }
    return result;
}

CpuUsage::TimedSample
CpuUsage::sample_or_wait()
{
    std::shared_future<TimedSample> my_future;
    {
        Guard guard(_lock);
        if (_sampling) {
            if (!_conflict) {
                _conflict = std::make_unique<SampleConflict>();
            }
            my_future = _conflict->future_sample;
        } else {
            _sampling = true;
        }
    }
    if (my_future.valid()) {
        return my_future.get();
    } else {
        return do_sample();
    }
}

CpuUsage::TimedSample
CpuUsage::sample()
{
    return self().sample_or_wait();
}

} // namespace
