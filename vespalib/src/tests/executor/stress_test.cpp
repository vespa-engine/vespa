// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/locale/c.h>
#include <thread>

using namespace vespalib;
using namespace std::literals;

uint32_t doStuff(uint32_t input) {
    char buf[128];
    for (uint32_t i = 0; i < sizeof(buf); ++i) {
        buf[i] = ((input + i) * i) & 0xff;
    }
    uint32_t result = 0;
    for (uint32_t i = 0; i < sizeof(buf); ++i) {
        result += ((buf[i] * i) + input) & 0xff;
    }
    return result;
}

struct CPUTask : public Executor::Task {
    uint32_t taskSize;
    uint32_t &result;
    CPUTask(uint32_t size, uint32_t &res) : taskSize(size), result(res) {}
    void run() override {
        uint32_t res = 0;
        for (uint32_t i = 0; i < taskSize; ++i) {
            res += doStuff(i);
        }
        result += res;
    }
};

struct SyncTask : public Executor::Task {
    Gate &gate;
    CountDownLatch &latch;
    SyncTask(Gate &g, CountDownLatch &l) : gate(g), latch(l) {}
    void run() override {
        latch.countDown();
        gate.await();
    }
};

class Test : public TestApp
{
private:
    uint32_t _result;

public:
    Test() : _result(0) {}
    uint32_t calibrate(double ms);
    void stress(Executor &executor, uint32_t taskSize, uint32_t numTasks);
    int Main() override;
};

uint32_t
Test::calibrate(double wanted_ms)
{
    uint32_t n = 0;
    vespalib::Timer t0;
    vespalib::Timer t1;
    { // calibration of calibration loop
        uint32_t result = 0;
        vespalib::duration dur;
        do {
            result += doStuff(++n);
            t1 = vespalib::Timer();
            dur = (t1.elapsed() - t0.elapsed());
        } while (dur < 1s);
        _result += result;
    }
    { // calibrate loop
        t0 = vespalib::Timer();
        uint32_t result = 0;
        for (uint32_t i = 0; i < n; ++i) {
            result += doStuff(i);
        }
        _result += result;
        t1 = vespalib::Timer();
    }
    double ms = vespalib::count_ms(t1.elapsed() - t0.elapsed());
    double size = (((double)n) / ms) * wanted_ms;
    return (uint32_t) std::round(size);
}

int
Test::Main()
{
    TEST_INIT("stress_test");
    if (_argc != 4) {
        fprintf(stderr, "Usage: %s <threads> <ms per task> <tasks>\n",
                _argv[0]);
        TEST_DONE();
    }
    uint32_t threads     = atoi(_argv[1]);
    double   ms_per_task = locale::c::strtod(_argv[2], 0);
    uint32_t tasks       = atoi(_argv[3]);
    fprintf(stderr, "threads    : %u\n", threads);
    fprintf(stderr, "ms per task: %g\n", ms_per_task);
    fprintf(stderr, "tasks      : %u\n", tasks);
    {
        fprintf(stderr, "calibrating task size...\n");
        uint32_t taskSize = calibrate(ms_per_task);
        fprintf(stderr, "calibrated task size: %u\n", taskSize);
        ThreadStackExecutor executor(threads, 128000, 5000 + threads);
        {
            Gate gate;
            CountDownLatch latch(threads);
            for (uint32_t i = 0; i < threads; ++i) {
                Executor::Task::UP res
                    = executor.execute(Executor::Task::UP(
                                               new SyncTask(gate, latch)));
                ASSERT_TRUE(res.get() == 0);
            }
            latch.await();
            gate.countDown();
            executor.sync();
            fprintf(stderr, "all threads have been accounted for...\n");
        }
        {
            vespalib::Timer t0;
            fprintf(stderr, "starting task submission...\n");
            uint32_t result = 0;
            for (uint32_t i = 0; i < tasks; ++i) {
                Executor::Task::UP t(new CPUTask(taskSize, result));
                t = executor.execute(std::move(t));
                while (t) {
                    std::this_thread::sleep_for(10ms);
                    t = executor.execute(std::move(t));
                }
            }
            executor.sync();
            double ms = vespalib::count_ms(t0.elapsed());
            fprintf(stderr, "total execution wall time: %g ms\n", ms);
            _result += result;
        }
    }
    TEST_DONE();
}
TEST_APPHOOK(Test);
