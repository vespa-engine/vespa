// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/simple_thread_bundle.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/box.h>
#include <thread>

using namespace vespalib;

uint64_t doWork(uint64_t data) {
    uint64_t value = data;
    for (size_t i = 0; i < 1_Mi; ++i) {
        value = (value << 16) + (value >> 8) + (value << 32);
    }
    return value;
}

struct Worker : Runnable {
    size_t iter;
    uint64_t input;
    uint64_t output;
    Worker() : iter(1), input(0), output(0) {}
    void init(size_t n, uint64_t i) {
        iter = n;
        input = i;
    }
    void run() override {
        uint64_t value = input;
        for (size_t i = 0; i < iter; ++i) {
            value = doWork(value);
        }
        output = value;
    }
};

TEST("estimate cost of thread bundle fork/join") {
    std::vector<SimpleThreadBundle::Strategy> strategy_value
        = make_box(SimpleThreadBundle::USE_SIGNAL_LIST,
                   SimpleThreadBundle::USE_SIGNAL_TREE,
                   SimpleThreadBundle::USE_BROADCAST);
    std::vector<std::string> strategy_name
        = make_box(std::string("USE_SIGNAL_LIST"),
                   std::string("USE_SIGNAL_TREE"),
                   std::string("USE_BROADCAST"));
    for (size_t strategy = 0; strategy < strategy_value.size(); ++strategy) {
        for (size_t threads = 1; threads <= 16; ++threads) {
            SimpleThreadBundle threadBundle(threads, strategy_value[strategy]);
            std::vector<Worker> workers(threads);
            std::vector<Runnable*> targets;
            for (size_t i = 0; i < threads; ++i) {
                targets.push_back(&workers[i]);
            }
            size_t iter = 0x4; // work done per fork
            size_t fork = 0x1; // number of forks performed
            for (; iter > 0; iter >>= 1, fork <<= 1) {
                for (size_t i = 0; i < threads; ++i) {
                    workers[i].init(iter, i);
                }
                double minTime = 1000000.0;
                for (size_t samples = 0; samples < 32; ++samples) {
                    vespalib::Timer timer;
                    for (size_t n = 0; n < fork; ++n) {
                        threadBundle.run(targets);
                    }
                    double time = vespalib::count_ms(timer.elapsed());
                    if (time < minTime) {
                        minTime = time;
                    }
                    std::this_thread::sleep_for(10ms);
                }
                fprintf(stderr, "strategy: %s, threads: %zu, fork: %zu, iter: %zu, time: %g, unit: %g\n",
                        strategy_name[strategy].c_str(), threads, fork, iter, minTime,
                        minTime / (fork * iter));
            }
        }
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
