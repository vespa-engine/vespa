// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fixed_capacity_fifo.h"

namespace vespalib {

template <typename T, std::unsigned_integral SizeT = size_t>
struct FifoQueue {
    using value_type = T;
    using size_type = SizeT;

    using FixedQueueType = FixedCapacityFifo<value_type, size_type>;

    FixedQueueType _queue;

    FifoQueue(size_type initial_capacity) : _queue(initial_capacity) {}

    [[nodiscard]] bool empty() const noexcept { return _queue.empty(); }
    [[nodiscard]] size_type size() const noexcept { return _queue.size(); }
    [[nodiscard]] size_type capacity() const noexcept { return _queue.capacity(); }

    template <typename T1>
    void emplace_back(T1&& val) noexcept(noexcept(T(std::forward<T1>(val)))) {
        if (_queue.size() == _queue.capacity()) [[unlikely]] {
            grow();
        }
        _queue.emplace_back(std::forward<T1>(val));
    }

    // Precondition: !empty()
    const value_type& front() const noexcept {
        return _queue.front();
    }
    value_type& front() noexcept {
        return _queue.front();
    }

    // Precondition: !empty()
    void pop_front() noexcept {
        _queue.pop_front();
    }

    void grow() {
        size_type new_capacity = _queue.capacity() * 2;
        FixedQueueType tmp(std::move(_queue), new_capacity);
        std::swap(_queue, tmp);
    }
};

}
