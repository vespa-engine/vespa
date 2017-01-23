// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <vespa/vespalib/tensor/types.h>

namespace vespalib {
namespace eval { class ValueType; }
namespace tensor {

class SparseTensorAddressBuilder;

/**
 * A builder that buffers up a tensor address with unsorted
 * dimensions.
 */
class SparseTensorUnsortedAddressBuilder
{
    struct ElementStringRef
    {
        uint32_t _base;
        uint32_t _len;
        ElementStringRef(uint32_t base, uint32_t len)
            : _base(base), _len(len)
        {
        }
        vespalib::stringref asStringRef(const char *base) const
        {
            return vespalib::stringref(base + _base, _len);
        }
    };
    struct ElementRef
    {
        ElementStringRef _dimension;
        ElementStringRef _label;
        ElementRef(ElementStringRef dimension,
                   ElementStringRef label)
            : _dimension(dimension),
              _label(label)
        {
        }
        vespalib::stringref getDimension(const char *base) const {
            return _dimension.asStringRef(base);
        }
        vespalib::stringref getLabel(const char *base) const {
            return _label.asStringRef(base);
        }
    };
    std::vector<char> _elementStrings; // unsorted dimensions
    std::vector<ElementRef> _elements; // unsorted dimensions

    ElementStringRef
    append(vespalib::stringref str)
    {
        const char *cstr = str.c_str();
        uint32_t start = _elementStrings.size();
        _elementStrings.insert(_elementStrings.end(),
                               cstr, cstr + str.size() + 1);
        return ElementStringRef(start, str.size());
    }

public:
    SparseTensorUnsortedAddressBuilder();
    bool empty() const { return _elementStrings.empty(); }
    void add(vespalib::stringref dimension, vespalib::stringref label)
    {
        _elements.emplace_back(append(dimension), append(label));
    }
    /*
     * Sort the stored tensor address and pass it over to a strict
     * tensor address builder in sorted order.
     */
    void buildTo(SparseTensorAddressBuilder &builder,
                 const eval::ValueType &type);
    void clear() { _elementStrings.clear(); _elements.clear(); }
};


} // namespace vespalib::tensor
} // namespace vespalib
