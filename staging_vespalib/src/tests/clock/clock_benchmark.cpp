// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/clock.h>
#include <cassert>
#include <vector>

using vespalib::Clock;
using fastos::TimeStamp;

struct Sampler : public FastOS_Runnable {
    Sampler(Clock & clock, fastos::SteadyTimeStamp end) :
       _samples(0),
       _clock(clock),
       _theEnd(end)
   { }
    void Run(FastOS_ThreadInterface *, void *) override {
        printf("Starting at %s with doom at %s\n", _clock.getTimeNSAssumeRunning().toString().c_str(), _theEnd.toString().c_str());
        for (_samples = 0; (_clock.getTimeNSAssumeRunning() < _theEnd) && (_samples < 40000000000l); _samples++) {

        }
        printf("Took %ld clock samples at %s\n", _samples, _clock.getTimeNSAssumeRunning().toString().c_str());
    }
    uint64_t _samples;
    const Clock & _clock;
    fastos::SteadyTimeStamp _theEnd;
    FastOS_ThreadInterface * _thread;
};
void sample() {

}

int
main(int , char *argv[])
{
    long frequency = atoll(argv[1]);
    int numThreads = atoi(argv[2]);
    Clock clock(1.0/frequency);
    FastOS_ThreadPool pool(0x10000);
    assert(pool.NewThread(&clock, nullptr) != nullptr);

    std::vector<std::unique_ptr<Sampler>> threads;
    threads.reserve(numThreads);
    for (int i(0); i < numThreads; i++) {
        threads.push_back(std::make_unique<Sampler>(clock, fastos::ClockSteady::now() + 10*TimeStamp::SEC));
        Sampler * sampler = threads[i].get();
        sampler->_thread = pool.NewThread(threads[i].get(), nullptr);
    }
    for (const auto & sampler : threads) {
        sampler->_thread->Join();
    }
    pool.Close();
    clock.stop();
}
