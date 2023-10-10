// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "closeable.h"
#include <vespa/vespalib/util/priority_queue.h>
#include <memory>
#include <mutex>
#include <condition_variable>

namespace vbench {

/**
 * A thread-safe priority queue keeping track of objects queued
 * according to an abstract time line. After a time queue is closed,
 * all incoming objects will be deleted.
 **/
template <typename T>
class TimeQueue : public Closeable
{
private:
    struct Entry {
        std::unique_ptr<T> object;
        double time;
        Entry(std::unique_ptr<T> obj, double t) noexcept : object(std::move(obj)), time(t) {}
        Entry(Entry &&rhs) noexcept : object(std::move(rhs.object)), time(rhs.time) {}
        Entry &operator=(Entry &&rhs) noexcept {
            object = std::move(rhs.object);
            time = rhs.time;
            return *this;
        }
        bool operator<(const Entry &rhs) const noexcept {
            return (time < rhs.time);
        }
    };

    std::mutex                     _lock;
    std::condition_variable        _cond;
    double                         _time;
    double                         _window;
    double                         _tick;
    vespalib::PriorityQueue<Entry> _queue;
    bool                           _closed;

public:
    TimeQueue(double window, double tick);
    ~TimeQueue();
    void close() override;
    void discard();
    void insert(std::unique_ptr<T> obj, double time);
    bool extract(double time, std::vector<std::unique_ptr<T> > &list, double &delay);
};

} // namespace vbench

#include "time_queue.hpp"

