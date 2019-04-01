// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_address.h"
#include <algorithm>
#include <ostream>

namespace vespalib::tensor {

const vespalib::string TensorAddress::Element::UNDEFINED_LABEL = "(undefined)";

TensorAddress::Element::~Element() = default;

TensorAddress::TensorAddress()
    : _elements()
{
}

TensorAddress::~TensorAddress() = default;

TensorAddress::TensorAddress(const Elements &elements_in)
    : _elements(elements_in)
{
    std::sort(_elements.begin(), _elements.end());
}

bool
TensorAddress::hasDimension(const vespalib::string &dimension) const
{
    for (const auto &elem : _elements) {
        if (elem.dimension() == dimension) {
            return true;
        }
    }
    return false;
}

bool
TensorAddress::operator<(const TensorAddress &rhs) const
{
    if (_elements.size() == rhs._elements.size()) {
        for (size_t i = 0; i < _elements.size(); ++i) {
            if (_elements[i] != rhs._elements[i]) {
                return _elements[i] < rhs._elements[i];
            }
        }
    }
    return _elements.size() < rhs._elements.size();
}

bool
TensorAddress::operator==(const TensorAddress &rhs) const
{
    return _elements == rhs._elements;
}

size_t
TensorAddress::hash() const
{
    size_t hashCode = 1;
    for (const auto &elem : _elements) {
        hashCode = 31 * hashCode + elem.hash();
    }
    return hashCode;
}

std::ostream &
operator<<(std::ostream &out, const TensorAddress::Elements &elements)
{
    out << "{";
    bool first = true;
    for (const auto &elem : elements) {
        if (!first) {
            out << ",";
        }
        out << elem.dimension() << ":" << elem.label();
        first = false;
    }
    out << "}";
    return out;
}

std::ostream &
operator<<(std::ostream &out, const TensorAddress &value)
{
    out << value.elements();
    return out;
}

}
