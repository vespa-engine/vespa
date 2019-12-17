// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/clock.h>
#include <vespa/fastos/thread.h>
#include <cassert>
#include <vector>
#include <atomic>
#include <cstring>
#include <condition_variable>
#include <mutex>

using vespalib::Clock;
using fastos::TimeStamp;

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

class TestClock : public FastOS_Runnable
{
private:
    int                       _timePeriodMS;
    std::mutex                _lock;
    std::condition_variable   _cond;
    UpdateClock              &_clock;
    bool                      _stop;

    void Run(FastOS_ThreadInterface *thisThread, void *arguments) override;

public:
    TestClock(UpdateClock & clock, double timePeriod)
        : _timePeriodMS(static_cast<uint32_t>(timePeriod*1000)),
          _lock(),
          _cond(),
          _clock(clock),
          _stop(false)
    { }
    ~TestClock() {
        std::lock_guard<std::mutex> guard(_lock);
        _stop = true;
        _cond.notify_all();
    }
};

void TestClock::Run(FastOS_ThreadInterface *thread, void *)
{
    std::unique_lock<std::mutex> guard(_lock);
    while ( ! thread->GetBreakFlag() && !_stop) {
        _clock.update();
        _cond.wait_for(guard, std::chrono::milliseconds(_timePeriodMS));
    }
}

struct SamplerBase : public FastOS_Runnable {
    SamplerBase(uint32_t threadId)
        : _thread(nullptr),
          _threadId(threadId),
          _samples(0),
          _count()
    {
        memset(_count, 0, sizeof(_count));
    }
    FastOS_ThreadInterface * _thread;
    uint32_t _threadId;
    uint64_t _samples;
    uint64_t _count[3];
};

template<typename Func>
struct Sampler : public SamplerBase {
    Sampler(Func func, uint32_t threadId)
        : SamplerBase(threadId),
          _func(func)
    { }
    void Run(FastOS_ThreadInterface *, void *) override {
        uint64_t samples;
        fastos::SteadyTimeStamp prev = _func();
        for (samples = 0; (samples < _samples); samples++) {
            fastos::SteadyTimeStamp now = _func();
            fastos::TimeStamp diff = now - prev;
            if (diff > 0) prev = now;
            _count[1 + ((diff == 0) ? 0 : (diff > 0) ? 1 : -1)]++;
        }

    }
    Func     _func;
};

template<typename Func>
void benchmark(const char * desc, FastOS_ThreadPool & pool, uint64_t samples, uint32_t numThreads, Func func) {
    std::vector<std::unique_ptr<SamplerBase>> threads;
    threads.reserve(numThreads);
    fastos::SteadyTimeStamp start = fastos::ClockSteady::now();
    for (uint32_t i(0); i < numThreads; i++) {
        SamplerBase * sampler = new Sampler<Func>(func, i);
        sampler->_samples = samples;
        sampler->_thread = pool.NewThread(sampler, nullptr);
        threads.emplace_back(sampler);
    }
    uint64_t count[3];
    memset(count, 0, sizeof(count));
    for (const auto & sampler : threads) {
        sampler->_thread->Join();
        for (uint32_t i(0); i < 3; i++) {
            count[i] += sampler->_count[i];
        }
    }
    printf("%s: Took %ld clock samples in %2.3f with [%ld, %ld, %ld] counts\n", desc, samples, (fastos::ClockSteady::now() - start).sec(), count[0], count[1], count[2]);
}

int
main(int , char *argv[])
{
    uint64_t frequency = atoll(argv[1]);
    uint32_t numThreads = atoi(argv[2]);
    uint64_t samples = atoll(argv[3]);
    FastOS_ThreadPool pool(0x10000);
    NSValue nsValue;
    NSVolatile nsVolatile;
    NSAtomic nsAtomic;
    Clock clock(1.0/frequency);
    TestClock nsClock(nsValue, 1.0/frequency);
    TestClock nsVolatileClock(nsVolatile, 1.0/frequency);
    TestClock nsAtomicClock(nsAtomic, 1.0/frequency);
    assert(pool.NewThread(clock.getRunnable(), nullptr) != nullptr);
    assert(pool.NewThread(&nsClock, nullptr) != nullptr);
    assert(pool.NewThread(&nsVolatileClock, nullptr) != nullptr);
    assert(pool.NewThread(&nsAtomicClock, nullptr) != nullptr);

    benchmark("vespalib::Clock", pool, samples, numThreads, [&clock]() {
        return clock.getTimeNSAssumeRunning();
    });
    benchmark("uint64_t", pool, samples, numThreads, [&nsValue]() {
        return fastos::SteadyTimeStamp(nsValue._value) ;
    });
    benchmark("volatile uint64_t", pool, samples, numThreads, [&nsVolatile]() {
        return fastos::SteadyTimeStamp(nsVolatile._value) ;
    });
    benchmark("memory_order_relaxed", pool, samples, numThreads, [&nsAtomic]() {
        return fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_relaxed)) ;
    });
    benchmark("memory_order_consume", pool, samples, numThreads, [&nsAtomic]() {
        return fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_consume)) ;
    });
    benchmark("memory_order_acquire", pool, samples, numThreads, [&nsAtomic]() {
        return fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_acquire)) ;
    });
    benchmark("memory_order_seq_cst", pool, samples, numThreads, [&nsAtomic]() {
        return fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_seq_cst)) ;
    });

    benchmark("fastos::ClockSteady::now()", pool, samples, numThreads, []() {
        return fastos::ClockSteady::now();
    });

    pool.Close();
    clock.stop();
    return 0;
}
