// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sync.h"
#include "count_down_latch.h"
#include "thread.h"
#include "runnable.h"
#include "thread_bundle.h"
#include "noncopyable.hpp"

namespace vespalib {

namespace fixed_thread_bundle {

/**
 * collection of work to be done by a single call to the thread bundle
 * run function, and latch to count down when each part is done. The
 * same data structure will be reused for all calls to run in order to
 * support static wiring of signal paths and execution hooks.
 **/
struct Work {
    const std::vector<Runnable *> *targets;
    CountDownLatch *latch;
    Work() : targets(0), latch(0) {}
};

/**
 * the subset of work to be done by a single thread.
 **/
struct Part {
    const Work &work;
    size_t offset;
    Part(const Work &w, size_t o) : work(w), offset(o) {}
    bool valid() { return (offset < work.targets->size()); }
    void perform() {
        if (valid()) {
            (*(work.targets))[offset]->run();
        }
        work.latch->countDown();
    }
};

/**
 * countable signal path between threads.
 **/
struct Signal {
    bool valid;
    size_t generation;
    Monitor monitor;
    Signal() noexcept : valid(true), generation(0), monitor() {}
    size_t wait(size_t &localGen) {
        MonitorGuard guard(monitor);
        while (localGen == generation) {
            guard.wait();
        }
        size_t diff = (generation - localGen);
        localGen = generation;
        return (valid ? diff : 0);
    }
    void send() {
        MonitorGuard guard(monitor);
        ++generation;
        guard.signal();
    }
    void broadcast() {
        MonitorGuard guard(monitor);
        ++generation;
        guard.broadcast();
    }
    void cancel() {
        MonitorGuard guard(monitor);
        ++generation;
        valid = false;
        guard.broadcast();
    }
};

} // namespace vespalib::fixed_thread_bundle

/**
 * A ThreadBundle implementation employing a fixed set of internal
 * threads. The internal Pool class can be used to recycle bundles.
 **/
class SimpleThreadBundle : public ThreadBundle
{
public:
    typedef fixed_thread_bundle::Work Work;
    typedef fixed_thread_bundle::Signal Signal;

    typedef std::unique_ptr<SimpleThreadBundle> UP;
    enum Strategy { USE_SIGNAL_LIST, USE_SIGNAL_TREE, USE_BROADCAST };

    class Pool
    {
    private:
        Lock _lock;
        size_t _bundleSize;
        std::vector<SimpleThreadBundle*> _bundles;

    public:
        Pool(size_t bundleSize);
        ~Pool();
        SimpleThreadBundle::UP obtain();
        void release(SimpleThreadBundle::UP bundle);
    };

private:
    struct Worker : Runnable, noncopyable {
        using UP = std::unique_ptr<Worker>;
        Thread thread;
        Signal &signal;
        Runnable::UP hook;
        Worker(Signal &s, Runnable::UP h) : thread(*this), signal(s), hook(std::move(h)) {
            thread.start();
        }
        void run() override {
            for (size_t gen = 0; signal.wait(gen) > 0; ) {
                hook->run();
            }
        }
    };

    Work                    _work;
    std::vector<Signal>     _signals;
    std::vector<Worker::UP> _workers;
    Runnable::UP            _hook;

public:
    SimpleThreadBundle(size_t size, Strategy strategy = USE_SIGNAL_LIST);
    ~SimpleThreadBundle();
    size_t size() const override;
    void run(const std::vector<Runnable*> &targets) override;
};

} // namespace vespalib

