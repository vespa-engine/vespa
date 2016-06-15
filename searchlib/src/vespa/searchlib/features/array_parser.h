// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace features {

/**
 * Utility for parsing a string representation of an array with values (numeric or string)
 * that is typically passed down with the query.
 *
 * The format of the array is as follows:
 * 1) Dense form: [value0 value1 ... valueN] (where value0 has index 0)
 *
 * 2) Sparse form: {idxA:valueA,idxB:valueB,...,idxN:valueN}.
 *    In the sparse form all non-specified indexes get the value 0.0 and
 *    has values for indexes in the range [0,max index specified].
 *    The parsed array is sorted in index order.
 */
class ArrayParser
{
private:
    static void logWarning(const vespalib::string &msg);

public:
    template <typename T>
    class ValueAndIndex {
    public:
        typedef T ValueType;
        ValueAndIndex(T value, uint32_t index) : _value(value), _index(index) { }
        T getValue() const { return _value; }
        uint32_t getIndex() const { return _index; }
        bool operator < (const ValueAndIndex & b) const { return _index < b._index; }
    private:
        T        _value;
        uint32_t _index;
    };

    template <typename OutputType>
    static void parse(const vespalib::string &input, OutputType &output);

    template <typename OutputType>
    static void parsePartial(const vespalib::string &input, OutputType &output);
};

} // namespace features
} // namespace search
