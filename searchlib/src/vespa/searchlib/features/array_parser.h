// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search::features {

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
public:
    template <typename T>
    class ValueAndIndex {
    public:
        using ValueType = T;
        ValueAndIndex(T value, uint32_t index) noexcept : _value(value), _index(index) { }
        T getValue() const { return _value; }
        uint32_t getIndex() const { return _index; }
        bool operator < (const ValueAndIndex & b) const { return _index < b._index; }
    private:
        T        _value;
        uint32_t _index;
    };

    template <typename OutputType, typename T = typename OutputType::value_type>
    static void parse(const vespalib::string &input, OutputType &output);

    static void parse(const vespalib::string &input, std::vector<int8_t> &output);

    template <typename OutputType>
    static void parsePartial(const vespalib::string &input, OutputType &output);
};

}
