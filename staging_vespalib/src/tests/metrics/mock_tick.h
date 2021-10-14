// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <condition_variable>
#include <mutex>
#include <vespa/vespalib/metrics/clock.h>
#include <vespa/vespalib/testkit/test_kit.h>

namespace vespalib::metrics {

// used to test clients of the Tick interface
// values shared between threads are bounded queues with max size 1
class MockTick : public Tick {
private:
    using Guard = std::unique_lock<std::mutex>;
    struct Value {
        Value() noexcept : value(0.0), valid(false) {}
        TimeStamp value;
        bool    valid;
    };

    TimeStamp               _first_value;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _alive;
    Value                   _prev;
    Value                   _next;

    void push(Value &dst, TimeStamp value) {
        Guard guard(_lock);
        while (_alive && dst.valid) {
            _cond.wait(guard);
        }
        dst.value = value;
        dst.valid = true;
        _cond.notify_one();
    }

    TimeStamp pop(Value &src) {
        Guard guard(_lock);
        while (_alive && !src.valid) {
            _cond.wait(guard);
        }
        src.valid = false;
        _cond.notify_one();
        return src.value;
    }

    TimeStamp peek(const Value &src) {
        Guard guard(_lock);
        while (_alive && !src.valid) {
            _cond.wait(guard);
        }
        return src.value;
    }

public:
    explicit MockTick(TimeStamp first_value) noexcept
        : _first_value(first_value), _lock(), _cond(), _alive(true), _prev(), _next() {}
    TimeStamp first() override { return _first_value; }
    TimeStamp next(TimeStamp prev) override {
        push(_prev, prev);
        return pop(_next);
    }
    TimeStamp give(TimeStamp next_value) {
        TimeStamp prev_value = pop(_prev);
        push(_next, next_value);
        EXPECT_EQUAL(peek(_prev).count(), next_value.count());
        return prev_value;
    }
    bool alive() const override { return _alive; }
    void kill() override {
        Guard guard(_lock);
        _alive = false;
        _cond.notify_all();
    }
};

// share the MockTick between the tested and the tester.
class TickProxy : public Tick {
private:
    std::shared_ptr<Tick> _tick;
public:
    explicit TickProxy(std::shared_ptr<Tick> tick) noexcept : _tick(std::move(tick)) {}
    TimeStamp first() override { return _tick->first(); }
    TimeStamp next(TimeStamp prev) override { return _tick->next(prev); }
    bool alive() const override { return _tick->alive(); }
    void kill() override { _tick->kill(); }
};

} // namespace vespalib::metrics
