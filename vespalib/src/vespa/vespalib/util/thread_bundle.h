// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "runnable.h"
#include <vector>
#include <ranges>

namespace vespalib {

namespace thread_bundle {

template <typename T>
concept direct_dispatch_array = std::ranges::contiguous_range<T> &&
    std::ranges::sized_range<T> &&
    (std::is_same_v<std::ranges::range_value_t<T>,Runnable*> ||
     std::is_same_v<std::ranges::range_value_t<T>,Runnable::UP>);

}

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
    template <thread_bundle::direct_dispatch_array Array>
    void run(const Array &items) {
        static_assert(sizeof(std::ranges::range_value_t<Array>) == sizeof(Runnable*));
        run(reinterpret_cast<Runnable* const*>(std::ranges::data(items)), std::ranges::size(items));
    }

    // convenience run wrapper
    template <std::ranges::range List>
    requires (!thread_bundle::direct_dispatch_array<List>)
    void run(List &items) {
        std::vector<Runnable*> targets;
        if constexpr (std::ranges::sized_range<List>) {
            targets.reserve(std::ranges::size(items));
        }
        for (auto &item: items) {
            targets.push_back(resolve(item));
        }
        run(targets.data(), targets.size());
    }

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~ThreadBundle() {}

    // a thread bundle that can only run things in the current thread.
    static ThreadBundle &trivial();
    
private:
    Runnable *resolve(Runnable *target) { return target; }
    Runnable *resolve(Runnable &target) { return &target; }
    template <typename T>
    Runnable *resolve(const std::unique_ptr<T> &target) { return target.get(); }
};

} // namespace vespalib
