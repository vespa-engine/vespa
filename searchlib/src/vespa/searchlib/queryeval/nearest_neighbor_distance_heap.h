// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/priority_queue.h>
#include <mutex>
#include <limits>
#include <atomic>

namespace search::queryeval {

/**
 * A heap of the K closest distances that can be shared between multiple search iterators.
 **/
class NearestNeighborDistanceHeap {
private:
    std::mutex _lock;
    size_t _size;
    std::atomic<double> _distance_threshold;
    vespalib::PriorityQueue<double, std::greater<double>> _priQ;
public:
    explicit NearestNeighborDistanceHeap(size_t maxSize)
      : _size(maxSize), _distance_threshold(std::numeric_limits<double>::max()),
        _priQ()
    {
        _priQ.reserve(maxSize);
    }
    void set_distance_threshold(double distance_threshold) {
        _distance_threshold.store(distance_threshold, std::memory_order_relaxed);
    }
    double distanceLimit() {
        return _distance_threshold.load(std::memory_order_relaxed);
    }
    void used(double distance) {
        std::lock_guard<std::mutex> guard(_lock);
        if (_priQ.size() < _size) {
            _priQ.push(distance);
        } else if (distance < _priQ.front()) {
            _priQ.front() = distance;
            _priQ.adjust();
        }
        if (_priQ.size() >= _size) {
            if (_distance_threshold.load(std::memory_order_relaxed) > _priQ.front()) {
                _distance_threshold.store(_priQ.front(), std::memory_order_relaxed);
            }
        }
    }
};

}
