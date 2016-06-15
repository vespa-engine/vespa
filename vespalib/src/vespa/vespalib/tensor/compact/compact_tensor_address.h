// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <iostream>
#include <vector>
#include "compact_tensor_address_ref.h"
#include <vespa/vespalib/tensor/types.h>

namespace vespalib {
namespace tensor {

/**
 * A compact sparse immutable address to a tensor cell.
 *
 * Only dimensions which have a different label than "undefined" are
 * explicitly included.
 *
 * Tensor addresses are ordered by the natural order of the elements
 * in sorted order.
 */
class CompactTensorAddress
{
public:
    class Element
    {
    private:
        vespalib::stringref _dimension;
        vespalib::stringref _label;

    public:
        Element(vespalib::stringref dimension_in,
                vespalib::stringref label_in)
            : _dimension(dimension_in), _label(label_in)
        {}
        vespalib::stringref dimension() const { return _dimension; }
        vespalib::stringref label() const { return _label; }
        bool operator<(const Element &rhs) const {
            if (_dimension == rhs._dimension) {
                // Define sort order when dimension is the same to be able
                // to do set operations over element vectors.
                return _label < rhs._label;
            }
            return _dimension < rhs._dimension;
        }
        bool operator==(const Element &rhs) const {
            return (_dimension == rhs._dimension) && (_label == rhs._label);
        }
        bool operator!=(const Element &rhs) const {
            return !(*this == rhs);
        }
    };

    typedef std::vector<Element> Elements;

private:
    Elements _elements;

public:
    CompactTensorAddress();
    explicit CompactTensorAddress(const Elements &elements_in);
    const Elements &elements() const { return _elements; }
    bool hasDimension(const vespalib::string &dimension) const;
    bool operator<(const CompactTensorAddress &rhs) const;
    bool operator==(const CompactTensorAddress &rhs) const;
    void deserializeFromSparseAddressRef(CompactTensorAddressRef ref);
    void deserializeFromAddressRefV2(CompactTensorAddressRef ref,
                                     const TensorDimensions &dimensions);
};

std::ostream &operator<<(std::ostream &out, const CompactTensorAddress::Elements &elements);
std::ostream &operator<<(std::ostream &out, const CompactTensorAddress &value);

} // namespace vespalib::tensor
} // namespace vespalib
