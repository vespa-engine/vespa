// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/databuffer.h>

namespace search::predicate {

/**
 * Stores a simple interval for the boolean constraint interval algorithm.
 */
struct Interval {
    uint32_t interval;

    Interval() : interval(0) {}
    Interval(uint32_t interval_) : interval(interval_) {}

    void serialize(vespalib::DataBuffer &buffer) const {
        buffer.writeInt32(interval);
    }
    static Interval deserialize(vespalib::DataBuffer &buffer) {
        return Interval{buffer.readInt32()};
    }
    bool operator==(const Interval &other) const {
        return interval == other.interval;
    }
    bool valid() const {
        return interval != 0;
    }
};
std::ostream &operator<<(std::ostream &out, const Interval &i);

/**
 * Stores an interval and bounds information for edge cases of range
 * searches in the boolean constraint interval algorithm.
 */
struct IntervalWithBounds {
    uint32_t interval;
    uint32_t bounds;

    IntervalWithBounds() : interval(0), bounds(0) {}
    IntervalWithBounds(uint32_t interval_, uint32_t bounds_) : interval(interval_), bounds(bounds_) {}

    void serialize(vespalib::DataBuffer &buffer) const {
        buffer.writeInt32(interval);
        buffer.writeInt32(bounds);
    }
    static IntervalWithBounds deserialize(vespalib::DataBuffer &buffer) {
        uint32_t interval = buffer.readInt32();
        uint32_t bounds = buffer.readInt32();
        return IntervalWithBounds{interval, bounds};
    }
    bool operator==(const IntervalWithBounds &other) const {
        return interval == other.interval && bounds == other.bounds;
    }
    bool valid() const {
        return interval != 0 && bounds != 0;
    }
};
std::ostream &operator<<(std::ostream &out, const IntervalWithBounds &i);

}
