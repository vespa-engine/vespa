// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/clock.h>
#include <cassert>
#include <vector>

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

struct NSVolatileValue : public UpdateClock {
    void update() override { _value = std::chrono::steady_clock::now().time_since_epoch().count(); }
    volatile int64_t _value;
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
    FastOS_ThreadInterface * _thread;
    uint64_t _samples;
};

template<typename Func>
struct Sampler : public SamplerBase {
    Sampler(Func func) :
       _func(func)
   { }
    void Run(FastOS_ThreadInterface *, void *) override {
        fastos::SteadyTimeStamp start = fastos::ClockSteady::now();
        printf("Starting at %s \n", fastos::ClockSystem::now().toString().c_str());
        uint64_t samples;
        uint64_t countFalse(0);
        for (samples = 0; (samples < _samples); samples++) {
            if ( ! _func(samples)) countFalse++;
        }
        printf("Took %ld clock samples in %2.3f with %ld keeping up\n", samples, (fastos::ClockSteady::now() - start).sec(), countFalse);
    }
    Func _func;
};

template<typename Func>
void benchmark(FastOS_ThreadPool & pool, uint64_t samples, int numThreads, Func func) {
    std::vector<std::unique_ptr<SamplerBase>> threads;
    threads.reserve(numThreads);
    for (int i(0); i < numThreads; i++) {
        SamplerBase * sampler = new Sampler(func);
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
    long frequency = atoll(argv[1]);
    int numThreads = atoi(argv[2]);
    uint64_t samples = atoll(argv[3]);
    FastOS_ThreadPool pool(0x10000);
    NSValue nsValue;
    NSVolatileValue nsVolatileValue;
    Clock clock(1.0/frequency);
    TestClock nsClock(nsValue, 1.0/frequency);
    TestClock nsVolatileClock(nsVolatileValue, 1.0/frequency);
    assert(pool.NewThread(&clock, nullptr) != nullptr);
    assert(pool.NewThread(&nsClock, nullptr) != nullptr);
    assert(pool.NewThread(&nsVolatileClock, nullptr) != nullptr);
    fastos::SteadyTimeStamp now = clock.getTimeNSAssumeRunning();
    FastOS_Thread::Sleep(100);

    benchmark(pool, samples, numThreads, [&clock, &now](int64_t i){ return (now+i < clock.getTimeNSAssumeRunning());});
    benchmark(pool, samples, numThreads, [&nsValue, &now](int64_t i){ return (now+i < fastos::SteadyTimeStamp(nsValue._value));});
    benchmark(pool, samples, numThreads, [&nsVolatileValue, &now](int64_t i){ return (now+i < fastos::SteadyTimeStamp(nsVolatileValue._value));});

    benchmark(pool, samples, numThreads, [&now](uint64_t i){ return (now+i < fastos::ClockSteady::now());});

    pool.Close();
    clock.stop();
    return 0;
}
