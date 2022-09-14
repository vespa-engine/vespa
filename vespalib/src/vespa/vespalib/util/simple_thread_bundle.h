// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "count_down_latch.h"
#include "thread.h"
#include "runnable.h"
#include "thread_bundle.h"

namespace vespalib {

namespace fixed_thread_bundle {

/**
 * collection of work to be done by a single call to the thread bundle
 * run function, and latch to count down when each part is done. The
 * same data structure will be reused for all calls to run in order to
 * support static wiring of signal paths and execution hooks.
 **/
struct Work {
    Runnable* const* targets;
    size_t cnt;
    CountDownLatch *latch;
    Work() : targets(nullptr), cnt(0), latch(0) {}
};

/**
 * the subset of work to be done by a single thread.
 **/
struct Part {
    const Work &work;
    size_t offset;
    Part(const Work &w, size_t o) : work(w), offset(o) {}
    bool valid() { return (offset < work.cnt); }
    void perform() {
        if (valid()) {
            work.targets[offset]->run();
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
    std::unique_ptr<std::mutex> monitor;
    std::unique_ptr<std::condition_variable> cond;
    Signal() noexcept;
    Signal(Signal &&) noexcept = default;
    ~Signal();
    size_t wait(size_t &localGen) {
        std::unique_lock guard(*monitor);
        while (localGen == generation) {
            cond->wait(guard);
        }
        size_t diff = (generation - localGen);
        localGen = generation;
        return (valid ? diff : 0);
    }
    void send() {
        std::lock_guard guard(*monitor);
        ++generation;
        cond->notify_one();
    }
    void broadcast() {
        std::lock_guard guard(*monitor);
        ++generation;
        cond->notify_all();
    }
    void cancel() {
        std::lock_guard guard(*monitor);
        ++generation;
        valid = false;
        cond->notify_all();
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
    using Work = fixed_thread_bundle::Work;
    using Signal = fixed_thread_bundle::Signal;
    using init_fun_t = Runnable::init_fun_t;

    typedef std::unique_ptr<SimpleThreadBundle> UP;
    enum Strategy { USE_SIGNAL_LIST, USE_SIGNAL_TREE, USE_BROADCAST };

    class Pool
    {
    private:
        std::mutex _lock;
        size_t     _bundleSize;
        init_fun_t _init_fun;
        std::vector<SimpleThreadBundle*> _bundles;

    public:
        Pool(size_t bundleSize, init_fun_t init_fun);
        Pool(size_t bundleSize) : Pool(bundleSize, Runnable::default_init_function) {}
        ~Pool();
        SimpleThreadBundle::UP obtain();
        void release(SimpleThreadBundle::UP bundle);
    };

private:
    struct Worker : Runnable {
        using UP = std::unique_ptr<Worker>;
        Thread thread;
        Signal &signal;
        Runnable::UP hook;
        Worker(Signal &s, init_fun_t init_fun, Runnable::UP h);
        void run() override;
    };

    Work                    _work;
    std::vector<Signal>     _signals;
    std::vector<Worker::UP> _workers;
    Runnable::UP            _hook;

public:
    SimpleThreadBundle(size_t size, init_fun_t init_fun, Strategy strategy = USE_SIGNAL_LIST);
    SimpleThreadBundle(size_t size, Strategy strategy = USE_SIGNAL_LIST)
      : SimpleThreadBundle(size, Runnable::default_init_function, strategy) {}
    ~SimpleThreadBundle();
    size_t size() const override;
    using ThreadBundle::run;
    void run(Runnable* const* targets, size_t cnt) override;
};

} // namespace vespalib

