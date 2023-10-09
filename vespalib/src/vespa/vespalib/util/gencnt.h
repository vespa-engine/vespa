// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <atomic>

namespace vespalib {

/**
 * @brief A GenCnt object wraps an integer generation counter.
 *
 * The value 0 is special, as when the internal counter wraps around
 * it will skip this value. This gives 0 a special meaning as the
 * first generation, and it will be different from all later
 * generations, even when the counter wraps around.
 **/
class GenCnt
{
private:
    std::atomic<uint32_t> _val;
public:

    /**
     * @brief Create a generation counter with value 0
     **/
    GenCnt() noexcept : _val(0) {}

    /**
     * @brief Create a generation counter with the given value
     *
     * @param val initial value
     **/
    GenCnt(uint32_t val) noexcept : _val(val) {}

    GenCnt(const GenCnt &rhs) noexcept : _val(rhs.getAsInt()) {}

    /**
     * @brief empty destructor
     **/
    ~GenCnt() = default;

    /**
     * @brief Increase the generation count held by this object
     *
     * @return this object, for chaining
     * @param n how much to increment the generation counter
     **/
    GenCnt &add(uint32_t n = 1);

    /**
     * @brief Check if this generation counter is inside the given
     * range
     *
     * @return true if this generation is inside the range
     * @param a the first generation in the range (inclusive)
     * @param b the last generation in the range (inclusive)
     **/
    bool inRangeInclusive(GenCnt a, GenCnt b) const;

    /**
     * @brief Get the distance in generations between this object and
     * the one given as 'other'.
     *
     * Note that this object is assumed to occur before 'other'.
     * @return distance between this and other
     * @param other other object occurring after this one
     **/
    uint32_t distance(const GenCnt &other) const;

    /**
     * @brief Check if this GenCnt is equal to another GenCnt
     *
     * @return true if <i>this</i> is equal to <i>rhs</i>
     * @param rhs the right hand side in the comparison
     **/
    bool operator==(const GenCnt &rhs) const { return rhs._val == _val; }

    /**
     * @brief Check if this GenCnt is not equal to another GenCnt
     *
     * @return true if <i>this</i> is not equal to <i>rhs</i>
     * @param rhs the right hand side in the comparison
     **/
    bool operator!=(const GenCnt &rhs) const { return rhs._val != _val; }

    /**
     * @brief Assignment operator
     *
     * @return this object
     * @param src the object we want to copy
     **/
    GenCnt &operator=(const GenCnt &src);

    /**
     * @brief Get the generation counter as an integer
     *
     * @return generation counter
     **/
    uint32_t getAsInt() const { return _val.load(std::memory_order_relaxed); }

    /**
     * @brief Set the generation counter from an integer
     *
     * @param val generation counter
     **/
    void setFromInt(uint32_t val) { _val = val; }

    /**
     * @brief Reset the value of this reference counter to 0
     **/
    void reset() { _val = 0; }
};

} // namespace vespalib

