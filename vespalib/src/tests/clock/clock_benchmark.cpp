// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>
#include <cassert>
#include <vector>
#include <atomic>
#include <cinttypes>
#include <cstring>
#include <condition_variable>
#include <mutex>
#include <thread>

using vespalib::Clock;
using vespalib::steady_time;
using vespalib::steady_clock;
using vespalib::duration;
using vespalib::to_s;

struct UpdateClock {
    virtual ~UpdateClock() {}
    virtual void update() = 0;
};

struct NSValue : public UpdateClock {
    void update() override { _value = std::chrono::steady_clock::now().time_since_epoch().count(); }
    int64_t _value;
};

struct NSVolatile : public UpdateClock {
    void update() override { _value = std::chrono::steady_clock::now().time_since_epoch().count(); }
    volatile int64_t _value;
};
struct NSAtomic : public UpdateClock {
    void update() override { _value.store(std::chrono::steady_clock::now().time_since_epoch().count()); }
    std::atomic<int64_t> _value;
};

class TestClock
{
private:
    int                       _timePeriodMS;
    std::mutex                _lock;
    std::condition_variable   _cond;
    UpdateClock              &_clock;
    bool                      _stop;
    std::thread               _thread;

    void run();

public:
    TestClock(UpdateClock & clock, double timePeriod)
        : _timePeriodMS(static_cast<uint32_t>(timePeriod*1000)),
          _lock(),
          _cond(),
          _clock(clock),
          _stop(false),
          _thread()
    {
        _thread = std::thread([this](){run();});
    }
    ~TestClock() {
        {
            std::lock_guard<std::mutex> guard(_lock);
            _stop = true;
            _cond.notify_all();
        }
        _thread.join();
    }
};

void TestClock::run()
{
    std::unique_lock<std::mutex> guard(_lock);
    while (!_stop) {
        _clock.update();
        _cond.wait_for(guard, std::chrono::milliseconds(_timePeriodMS));
    }
}

template<typename Func>
struct Sampler {
    Sampler(Func func, uint64_t samples)
      : _samples(samples),
        _count(),
        _func(func),
        _thread()
    {
        memset(_count, 0, sizeof(_count));
        _thread = std::thread([this](){run();});
    }
    void run() {
        steady_time prev = _func();
        for (uint64_t samples = 0; samples < _samples; ++samples) {
            steady_time now = _func();
            duration diff = now - prev;
            if (diff > duration::zero()) prev = now;
            _count[1 + ((diff == duration::zero()) ? 0 : (diff > duration::zero()) ? 1 : -1)]++;
        }
    }
    uint64_t    _samples;
    uint64_t    _count[3];
    Func        _func;
    std::thread _thread;
};

template<typename Func>
void benchmark(const char * desc, uint64_t samples, uint32_t numThreads, Func func) {
    std::vector<std::unique_ptr<Sampler<Func>>> threads;
    threads.reserve(numThreads);
    steady_time start = steady_clock::now();
    for (uint32_t i(0); i < numThreads; i++) {
        threads.push_back(std::make_unique<Sampler<Func>>(func, samples));
    }
    uint64_t count[3];
    memset(count, 0, sizeof(count));
    for (const auto & sampler : threads) {
        sampler->_thread.join();
        for (uint32_t i(0); i < 3; i++) {
            count[i] += sampler->_count[i];
        }
    }
    printf("%s: Took %" PRId64 " clock samples in %2.3f with [%" PRId64 ", %" PRId64 ", %" PRId64 "] counts\n", desc, samples, to_s(steady_clock::now() - start), count[0], count[1], count[2]);
}

int
main(int argc, char *argv[])
{
    if (argc != 4) {
        fprintf(stderr, "usage: %s <frequency> <numThreads> <samples>\n", argv[0]);
        return 1;
    }
    uint64_t frequency = atoll(argv[1]);
    uint32_t numThreads = atoi(argv[2]);
    uint64_t samples = atoll(argv[3]);
    NSValue nsValue;
    NSVolatile nsVolatile;
    NSAtomic nsAtomic;
    vespalib::InvokeServiceImpl invoker(vespalib::from_s(1.0/frequency));
    Clock clock(invoker.nowRef());
    TestClock nsClock(nsValue, 1.0/frequency);
    TestClock nsVolatileClock(nsVolatile, 1.0/frequency);
    TestClock nsAtomicClock(nsAtomic, 1.0/frequency);

    benchmark("vespalib::Clock", samples, numThreads, [&clock]() {
        return clock.getTimeNS();
    });
    benchmark("uint64_t", samples, numThreads, [&nsValue]() {
        return steady_time (duration(nsValue._value));
    });
    benchmark("volatile uint64_t", samples, numThreads, [&nsVolatile]() {
        return steady_time(duration(nsVolatile._value));
    });
    benchmark("memory_order_relaxed", samples, numThreads, [&nsAtomic]() {
        return steady_time(duration(nsAtomic._value.load(std::memory_order_relaxed)));
    });
    benchmark("memory_order_consume", samples, numThreads, [&nsAtomic]() {
        return steady_time(duration(nsAtomic._value.load(std::memory_order_consume)));
    });
    benchmark("memory_order_acquire", samples, numThreads, [&nsAtomic]() {
        return steady_time(duration(nsAtomic._value.load(std::memory_order_acquire)));
    });
    benchmark("memory_order_seq_cst", samples, numThreads, [&nsAtomic]() {
        return steady_time(duration(nsAtomic._value.load(std::memory_order_seq_cst)));
    });
    benchmark("vespalib::steady_time::now()", samples, numThreads, []() {
        return steady_clock::now();
    });
    return 0;
}
