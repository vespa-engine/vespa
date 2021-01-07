// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <limits>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::queryeval {

/**
 * A heap of the K closest distances that can be shared between multiple search iterators.
 **/
class NearestNeighborDistanceHeap {
private:
    std::mutex _lock;
    size_t _size;
    double _distance_threshold;
    vespalib::PriorityQueue<double, std::greater<double>> _priQ;
public:
    explicit NearestNeighborDistanceHeap(size_t maxSize)
      : _size(maxSize), _distance_threshold(std::numeric_limits<double>::max()),
        _priQ()
    {
        _priQ.reserve(maxSize);
    }
    void set_distance_threshold(double distance_threshold) {
        _distance_threshold = distance_threshold;
    }
    double distanceLimit() {
        std::lock_guard<std::mutex> guard(_lock);
        if (_priQ.size() < _size) {
            return _distance_threshold;
        }
        return _priQ.front();
    }
    void used(double distance) {
        std::lock_guard<std::mutex> guard(_lock);
        if (_priQ.size() < _size) {
            _priQ.push(distance);
        } else if (distance < _priQ.front()) {
            _priQ.front() = distance;
            _priQ.adjust();
        }
    }
};

}
