// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "runnable.h"
#include <vector>

namespace vespalib {

/**
 * Interface used to separate the ownership and deployment of a
 * collection of threads cooperating to perform a partitioned
 * operation in parallel.
 **/
struct ThreadBundle {
    /**
     * The size of the thread bundle is defined to be the maximum
     * number of runnables that can be performed in parallel by the
     * run function.
     *
     * @return size of this thread bundle
     **/
    virtual size_t size() const = 0;

    /**
     * Performs all the given runnables in parallel and waits for
     * their completion. This function cannot be called with more
     * targets than the size of this bundle.
     **/
    virtual void run(Runnable* const* targets, size_t cnt) = 0;

    // convenience run wrapper
    void run(const std::vector<Runnable*> &targets) {
        run(targets.data(), targets.size());
    }

    // convenience run wrapper
    void run(const std::vector<Runnable::UP> &targets) {
        static_assert(sizeof(Runnable::UP) == sizeof(Runnable*));
        run(reinterpret_cast<Runnable* const*>(targets.data()), targets.size());
    }

    template <typename T>
    static constexpr bool is_runnable_ptr() {
        return (std::is_same_v<T,Runnable*> || std::is_same_v<T,Runnable::UP>);
    }

    // convenience run wrapper
    template <typename Item>
    std::enable_if_t<!is_runnable_ptr<Item>(),void> run(std::vector<Item> &items) {
        std::vector<Runnable*> targets;
        targets.reserve(items.size());
        for (auto &item: items) {
            targets.push_back(resolve(item));
        }
        run(targets);
    }

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~ThreadBundle() {}

    // a thread bundle that can only run things in the current thread.
    static ThreadBundle &trivial();
    
private:
    Runnable *resolve(Runnable &target) { return &target; }
    template <typename T>
    Runnable *resolve(const std::unique_ptr<T> &target) { return target.get(); }
};

} // namespace vespalib
