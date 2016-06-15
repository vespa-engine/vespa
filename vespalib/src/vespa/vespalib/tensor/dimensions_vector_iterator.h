// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace tensor {

using DimensionsVector = std::vector<vespalib::stringref>;

/**
 * An iterator for a dimensions vector used to simplify 3-way merge
 * between two tensor addresses and a dimension vector.
 */
class DimensionsVectorIterator
{
    using InnerIterator = DimensionsVector::const_iterator;
    InnerIterator _itr;
    InnerIterator _itrEnd;
public:
    DimensionsVectorIterator(const DimensionsVector &dimensions)
        : _itr(dimensions.cbegin()),
          _itrEnd(dimensions.cend())
    {
    }
    bool valid() const { return (_itr != _itrEnd); }
    vespalib::stringref dimension() const { return *_itr; }
    template <typename Iterator>
    bool beforeDimension(const Iterator &rhs) const {
        if (!valid()) {
            return false;
        }
        if (!rhs.valid()) {
            return true;
        }
        return (*_itr < rhs.dimension());
    }
    bool atDimension(vespalib::stringref rhsDimension) const
    {
        return (valid() && (*_itr == rhsDimension));
    }
    void next() { ++_itr; }
};


} // namespace vespalib::tensor
} // namespace vespalib
