// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/clock.h>
#include <cassert>
#include <vector>
#include <atomic>
#include <cstring>

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
    SamplerBase(uint32_t threadId) : _thread(nullptr), _threadId(threadId), _samples(0) { }
    FastOS_ThreadInterface * _thread;
    uint32_t _threadId;
    uint64_t _samples;
};

template<typename Func>
struct Sampler : public SamplerBase {
    Sampler(Func func, uint32_t threadId) :
       SamplerBase(threadId),
       _func(func)
    { }
    void Run(FastOS_ThreadInterface *, void *) override {
        fastos::SteadyTimeStamp start = fastos::ClockSteady::now();
        printf("Starting at %s \n", fastos::ClockSystem::now().toString().c_str());
        uint64_t samples;
        uint64_t count[3];
        memset(count, 0, sizeof(count));
        for (samples = 0; (samples < _samples); samples++) {
            count[1 + _func(_threadId)]++;
        }
        printf("Took %ld clock samples in %2.3f with [%ld, %ld, %ld] counts\n", samples, (fastos::ClockSteady::now() - start).sec(), count[0], count[1], count[2]);

    }
    Func _func;
};

template<typename Func>
void benchmark(FastOS_ThreadPool & pool, uint64_t samples, uint32_t numThreads, Func func) {
    std::vector<std::unique_ptr<SamplerBase>> threads;
    threads.reserve(numThreads);
    for (uint32_t i(0); i < numThreads; i++) {
        SamplerBase * sampler = new Sampler<Func>(func, i);
        sampler->_samples = samples;
        sampler->_thread = pool.NewThread(sampler, nullptr);
        threads.emplace_back(sampler);
    }
    for (const auto & sampler : threads) {
        sampler->_thread->Join();
    }
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
    assert(pool.NewThread(&clock, nullptr) != nullptr);
    assert(pool.NewThread(&nsClock, nullptr) != nullptr);
    assert(pool.NewThread(&nsVolatileClock, nullptr) != nullptr);
    assert(pool.NewThread(&nsAtomicClock, nullptr) != nullptr);

    std::vector<fastos::SteadyTimeStamp> prev(numThreads);
    for (auto & ts : prev) { ts = clock.getTimeNSAssumeRunning(); }
    benchmark(pool, samples, numThreads, [&clock, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = clock.getTimeNSAssumeRunning();
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsValue._value); }
    benchmark(pool, samples, numThreads, [&nsValue, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsValue._value) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsVolatile._value); }
    benchmark(pool, samples, numThreads, [&nsVolatile, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsVolatile._value) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsAtomic._value.load()); }
    benchmark(pool, samples, numThreads, [&nsAtomic, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_relaxed)) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsAtomic._value.load()); }
    benchmark(pool, samples, numThreads, [&nsAtomic, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_consume)) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsAtomic._value.load()); }
    benchmark(pool, samples, numThreads, [&nsAtomic, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_acquire)) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });
    for (auto & ts : prev) { ts = fastos::SteadyTimeStamp(nsAtomic._value.load()); }
    benchmark(pool, samples, numThreads, [&nsAtomic, &prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::SteadyTimeStamp(nsAtomic._value.load(std::memory_order_seq_cst)) ;
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });

    for (auto & ts : prev) { ts = fastos::ClockSteady::now(); }
    benchmark(pool, samples, numThreads, [&prev](uint32_t threadId) {
        fastos::SteadyTimeStamp now = fastos::ClockSteady::now();
        auto diff = now - prev[threadId];
        if (diff > 0) prev[threadId] = now;
        return (diff == 0) ? 0 : (diff > 0) ? 1 : -1;
    });

    pool.Close();
    clock.stop();
    return 0;
}
