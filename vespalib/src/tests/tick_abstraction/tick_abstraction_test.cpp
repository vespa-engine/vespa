// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <chrono>
#include <memory>
#include <mutex>

using Guard = std::unique_lock<std::mutex>;
using seconds = std::chrono::duration<double, std::ratio<1,1>>;

std::ostream &operator <<(std::ostream &out, const seconds &s) {
    out << s.count();
    return out;
}

/**
 * Simple interface abstracting both timing and time measurement for
 * threads wanting to do stuff at regular intervals and also knowing
 * at what time stuff was done. All time stamps are represented as
 * number of seconds since epoch. The 'first' function will return the
 * initial time stamp and will not block. The 'next' function will
 * block until the next tick is due, then return the current time
 * stamp. The parameter passed to the 'next' function should always be
 * the most recently obtained time stamp (from either 'first' or
 * 'next'). Calling the 'kill' function will ensure that the 'next'
 * function will never block again. It will also make the 'alive'
 * function return false. This deliberate breakage is intended for
 * speedy shutdown.
 **/
struct Tick {
    using UP = std::unique_ptr<Tick>;
    using seconds = std::chrono::duration<double, std::ratio<1,1>>;
    virtual seconds first() = 0;
    virtual seconds next(seconds prev) = 0;
    virtual bool alive() const = 0;
    virtual void kill() = 0;
    virtual ~Tick() {}
};

// share the MockTick between the tested and the tester.
class TickProxy : public Tick {
private:
    std::shared_ptr<Tick> _tick;
public:
    TickProxy(std::shared_ptr<Tick> tick) : _tick(std::move(tick)) {}
    seconds first() override { return _tick->first(); }
    seconds next(seconds prev) override { return _tick->next(prev); }
    bool alive() const override { return _tick->alive(); }
    void kill() override { _tick->kill(); }
};

// used to test clients of the Tick interface
// values shared between threads are bounded queues with max size 1
class MockTick : public Tick {
private:
    struct Value {
        seconds value{0.0};
        bool    valid{false};
    };

    seconds                 _first_value;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _alive;
    Value                   _prev;
    Value                   _next;

    void push(Value &dst, seconds value) {
        Guard guard(_lock);
        while (_alive && dst.valid) {
            _cond.wait(guard);
        }
        dst.value = value;
        dst.valid = true;
        _cond.notify_one();
    }

    seconds pop(Value &src) {
        Guard guard(_lock);
        while (_alive && !src.valid) {
            _cond.wait(guard);
        }
        src.valid = false;
        _cond.notify_one();
        return src.value;
    }

    seconds peek(const Value &src) {
        Guard guard(_lock);
        while (_alive && !src.valid) {
            _cond.wait(guard);
        }
        return src.value;
    }

public:
    MockTick(seconds first_value)
        : _first_value(first_value), _lock(), _cond(), _alive(true), _prev(), _next() {}
    seconds first() override { return _first_value; }
    seconds next(seconds prev) override {
        push(_prev, prev);
        return pop(_next);
    }
    seconds give(seconds next_value) {
        seconds prev_value = pop(_prev);
        push(_next, next_value);
        EXPECT_EQUAL(peek(_prev), next_value);
        return prev_value;
    }
    bool alive() const override { return _alive; }
    void kill() override {
        Guard guard(_lock);
        _alive = false;
        _cond.notify_all();
    }
};

struct Fixture {
    std::shared_ptr<MockTick> mock = std::make_shared<MockTick>(Tick::seconds(1.0));
    Tick::UP tick = std::make_unique<TickProxy>(mock);
};

TEST_MT_FF("test mock tick", 2, Fixture(), vespalib::TimeBomb(60)) {
    if (thread_id == 0) {
        Tick &tick = *f1.tick;
        Tick::seconds ts = tick.first();
        fprintf(stderr, "first tick: %g\n", ts.count());
        TEST_BARRIER(); // ensure ctor does not block
        while (tick.alive()) {
            ts = tick.next(ts);
            if (tick.alive()) {
                fprintf(stderr, "next tick: %g\n", ts.count());
            }
        }
    } else {
        MockTick &mock = *f1.mock;
        TEST_BARRIER();
        EXPECT_EQUAL(mock.give(seconds(2.0)), seconds(1.0));
        EXPECT_EQUAL(mock.give(seconds(3.0)), seconds(2.0));
        EXPECT_EQUAL(mock.give(seconds(4.0)), seconds(3.0));
        EXPECT_EQUAL(mock.give(seconds(5.0)), seconds(4.0));
        EXPECT_EQUAL(mock.give(seconds(6.0)), seconds(5.0));
        EXPECT_EQUAL(mock.give(seconds(7.0)), seconds(6.0));
        mock.kill();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
