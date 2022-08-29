// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <cassert>
#include <cstddef>

namespace search::fef {

class TermFieldMatchData;

/**
 * Array of pointers to TermFieldMatchData instances.
 * Use this class to pass an ordered set of references
 * into e.g. iterators searching in multiple fields at once.
 * The array must either be totally empty, or contain
 * the appropriate number of valid references.
 **/
class TermFieldMatchDataArray
{
private:
    std::vector<TermFieldMatchData *> _array;

public:
    TermFieldMatchDataArray() = default;
    TermFieldMatchDataArray(TermFieldMatchDataArray &&) noexcept = default;
    TermFieldMatchDataArray & operator = (TermFieldMatchDataArray &&) noexcept = default;
    TermFieldMatchDataArray(const TermFieldMatchDataArray&) = default;
    TermFieldMatchDataArray & operator = (const TermFieldMatchDataArray &) = delete;
    ~TermFieldMatchDataArray();
    /**
     * Reserve space for a number of elements in order to reduce number of allocations.
     * @param size Number of elements to reserve space for.
     */
    void reserve(size_t sz) {
        _array.reserve(sz);
    }
    /**
     * add a pointer to the array.
     *
     * @return this object for chaining
     * @param value the pointer to be added
     **/
    TermFieldMatchDataArray &add(TermFieldMatchData *value) {
        assert(value != nullptr);
        _array.push_back(value);
        return *this;
    }

    /**
     * check that the array contains valid references.
     *
     * @return true if array not empty
     **/
    bool valid() const { return !_array.empty(); }

    /**
     * size of the array.
     *
     * @return the size
     **/
    size_t size() const { return _array.size(); }

    /**
     * get a pointer from the array.
     *
     * @return the pointer
     * @param i index of the pointer
     **/
    TermFieldMatchData *operator[] (size_t i) const {
        return _array[i];
    }
};

}
