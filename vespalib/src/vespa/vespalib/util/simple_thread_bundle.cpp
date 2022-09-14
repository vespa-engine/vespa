// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_thread_bundle.h"
#include "exceptions.h"
#include <cassert>

using namespace vespalib::fixed_thread_bundle;

namespace vespalib {

VESPA_THREAD_STACK_TAG(simple_thread_bundle_executor);

namespace {

struct SignalHook : Runnable {
    Signal &signal;
    SignalHook(Signal &s) : signal(s) {}
    void run() override { signal.send(); }
};

struct BroadcastHook : Runnable {
    Signal &signal;
    BroadcastHook(Signal &s) : signal(s) {}
    void run() override { signal.broadcast(); }
};

struct PartHook : Runnable {
    Part part;
    PartHook(const Part &p) : part(p) {}
    void run() override { part.perform(); }
};

struct HookPair : Runnable {
    Runnable::UP first;
    Runnable::UP second;
    HookPair(Runnable::UP f, Runnable::UP s) : first(std::move(f)), second(std::move(s)) {}
    void run() override {
        first->run();
        second->run();
    }
};

Runnable::UP wrap(Runnable *runnable) {
    return Runnable::UP(runnable);
}

Runnable::UP chain(Runnable::UP first, Runnable::UP second) {
    return std::make_unique<HookPair>(std::move(first), std::move(second));
}

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

Signal::Signal() noexcept
    : valid(true),
      generation(0),
      monitor(std::make_unique<std::mutex>()),
      cond(std::make_unique<std::condition_variable>())
{}
Signal::~Signal() = default;

SimpleThreadBundle::Pool::Pool(size_t bundleSize, init_fun_t init_fun)
    : _lock(),
      _bundleSize(bundleSize),
      _init_fun(init_fun),
      _bundles()
{
}

SimpleThreadBundle::Pool::~Pool()
{
    while (!_bundles.empty()) {
        delete _bundles.back();
        _bundles.pop_back();
    }
}

SimpleThreadBundle::UP
SimpleThreadBundle::Pool::obtain()
{
    {
        std::lock_guard guard(_lock);
        if (!_bundles.empty()) {
            SimpleThreadBundle::UP ret(_bundles.back());
            _bundles.pop_back();
            return ret;
        }
    }
    return std::make_unique<SimpleThreadBundle>(_bundleSize, _init_fun);
}

void
SimpleThreadBundle::Pool::release(SimpleThreadBundle::UP bundle)
{
    std::lock_guard guard(_lock);
    _bundles.push_back(bundle.get());
    bundle.release();
}

//-----------------------------------------------------------------------------

SimpleThreadBundle::SimpleThreadBundle(size_t size_in, Runnable::init_fun_t init_fun, Strategy strategy)
    : _work(),
      _signals(),
      _workers(),
      _hook()
{
    if (size_in == 0) {
        throw IllegalArgumentException("size must be greater than 0");
    }
    if (strategy == USE_BROADCAST) {
        _signals.resize(1); // share single signal
    } else {
        _signals.resize(size_in - 1); // separate signal per worker
    }
    size_t next_unwired = 1;
    for (size_t i = 0; i < size_in; ++i) {
        Runnable::UP hook(new PartHook(Part(_work, i)));
        if (strategy == USE_SIGNAL_TREE) {
            for (size_t child = 0; child < 2 && next_unwired < size_in; ++child, ++next_unwired) {
                hook = chain(wrap(new SignalHook(_signals[next_unwired - 1])), std::move(hook));
            }
        } else if (i == 0) { // first thread should wake others
            if (strategy == USE_BROADCAST) {
                hook = chain(wrap(new BroadcastHook(_signals[0])), std::move(hook));
            } else {
                assert(strategy == USE_SIGNAL_LIST);
                for (; next_unwired < size_in; ++next_unwired) {
                    hook = chain(wrap(new SignalHook(_signals[next_unwired - 1])), std::move(hook));
                }
            }
        }
        if (i == 0) {
            _hook = std::move(hook);
        } else {
            size_t signal_idx = (strategy == USE_BROADCAST) ? 0 : (i - 1);
            _workers.push_back(std::make_unique<Worker>(_signals[signal_idx], init_fun, std::move(hook)));
        }
    }
}

SimpleThreadBundle::~SimpleThreadBundle()
{
    for (size_t i = 0; i < _signals.size(); ++i) {
        _signals[i].cancel();
    }
    for (size_t i = 0; i < _workers.size(); ++i) {
        _workers[i]->thread.join();
    }
}

size_t
SimpleThreadBundle::size() const
{
    return (_workers.size() + 1);
}

void
SimpleThreadBundle::run(Runnable* const* targets, size_t cnt)
{
    if (cnt > size()) {
        throw IllegalArgumentException("too many targets");
    }
    if (cnt == 0) {
        return;
    }
    if (cnt == 1) {
        targets[0]->run();
        return;
    }
    CountDownLatch latch(size());
    _work.targets = targets;
    _work.cnt = cnt;
    _work.latch = &latch;
    _hook->run();
    latch.await();
}

SimpleThreadBundle::Worker::Worker(Signal &s, Runnable::init_fun_t init_fun, Runnable::UP h)
  : thread(*this, std::move(init_fun)),
    signal(s),
    hook(std::move(h))
{
    thread.start();
}

void
SimpleThreadBundle::Worker::run() {
    for (size_t gen = 0; signal.wait(gen) > 0; ) {
        hook->run();
    }
}

} // namespace vespalib
