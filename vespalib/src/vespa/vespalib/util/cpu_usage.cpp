// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cpu_usage.h"
#include "require.h"
#include <pthread.h>
#include <optional>
#include <cassert>

#include <sys/resource.h>

namespace vespalib {

namespace cpu_usage {

namespace {

class DummyThreadSampler : public ThreadSampler {
private:
    steady_time _start;
    double _util;
public:
    DummyThreadSampler(double util) : _start(steady_clock::now()), _util(util) {}
    duration sample() const noexcept override {
        return from_s(to_s(steady_clock::now() - _start) * _util);
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
    duration sample() const noexcept override {
        timespec ts;
        memset(&ts, 0, sizeof(ts));
        clock_gettime(_my_clock, &ts);
        return from_timespec(ts);
    }
};

#endif

} // <unnamed>

duration total_cpu_usage() noexcept {
        timespec ts;
        memset(&ts, 0, sizeof(ts));
        clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts);
        return from_timespec(ts);
}

ThreadSampler::UP create_thread_sampler(bool force_mock_impl, double expected_util) {
    if (force_mock_impl) {
        return std::make_unique<DummyThreadSampler>(expected_util);
    }
#ifdef __linux__
    return std::make_unique<LinuxThreadSampler>();
#endif
    return std::make_unique<DummyThreadSampler>(expected_util);
}

} // cpu_usage

CpuUsage::ThreadTrackerImpl::ThreadTrackerImpl(cpu_usage::ThreadSampler::UP sampler)
  : _lock(),
    _cat(Category::OTHER),
    _old_usage(),
    _sampler(std::move(sampler)),
    _pending()
{
}

CpuUsage::Category
CpuUsage::ThreadTrackerImpl::set_category(Category new_cat) noexcept
{
    // only owning thread may change category
    if (new_cat == _cat) {
        return new_cat;
    }
    Guard guard(_lock);
    duration new_usage = _sampler->sample();
    if (_cat != Category::OTHER) {
        _pending[_cat] += (new_usage - _old_usage);
    }
    _old_usage = new_usage;
    auto old_cat = _cat;
    _cat = new_cat;
    return old_cat;
}

CpuUsage::Sample
CpuUsage::ThreadTrackerImpl::sample() noexcept
{
    Guard guard(_lock);
    if (_cat != Category::OTHER) {
        duration new_usage = _sampler->sample();
        _pending[_cat] += (new_usage - _old_usage);
        _old_usage = new_usage;
    }
    Sample sample = _pending;
    _pending = Sample();
    return sample;
}

vespalib::string &
CpuUsage::name_of(Category cat)
{
    static std::array<vespalib::string,num_categories> names = {"setup", "read", "write", "compact", "other"};
    return names[index_of(cat)];
}

CpuUsage::Category
CpuUsage::MyUsage::set_cpu_category_for_this_thread(Category cat) noexcept
{
    struct Wrapper {
        std::shared_ptr<ThreadTrackerImpl> self;
        Wrapper() : self(std::make_shared<ThreadTrackerImpl>(cpu_usage::create_thread_sampler())) {
            CpuUsage::self().add_thread(self);
        }
        ~Wrapper() {
            self->set_category(CpuUsage::Category::OTHER);
            CpuUsage::self().remove_thread(std::move(self));
        }
    };
    thread_local Wrapper wrapper;
    return wrapper.self->set_category(cat);
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

CpuUsage::~CpuUsage() = default;

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
    auto total = cpu_usage::total_cpu_usage();
    for (size_t i = 0; i < index_of(Category::OTHER); ++i) {
        total -= my_sample[i];
    }
    my_sample[Category::OTHER] = std::max(total, duration::zero());
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
            _conflict->waiters++;
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

Runnable::init_fun_t
CpuUsage::wrap(Runnable::init_fun_t init, Category cat)
{
    return [init,cat](Runnable &target) {
        auto my_usage = CpuUsage::use(cat);
        return init(target);
    };
}

Executor::Task::UP
CpuUsage::wrap(Executor::Task::UP task, Category cat)
{
    struct CpuTask : Executor::Task {
        UP task;
        Category cat;
        CpuTask(UP task_in, Category cat_in)
          : task(std::move(task_in)), cat(cat_in) {}
        void run() override {
            auto my_usage = CpuUsage::use(cat);
            task->run();
        }
    };
    return std::make_unique<CpuTask>(std::move(task), cat);
}

} // namespace
