// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor_address.h"
#include "sparse_tensor_address_decoder.h"
#include <algorithm>

namespace vespalib {
namespace tensor {

namespace
{

void
setupElements(CompactTensorAddress::Elements &elements,
              CompactTensorAddressRef ref)
{
    const char *cur = static_cast<const char *>(ref.start());
    const char *end = cur + ref.size();
    while (cur != end) {
        const char *dim = cur;
        while (*cur) {
            ++cur;
        }
        ++cur;
        const char *label = cur;
        while (*cur) {
            ++cur;
        }
        ++cur;
        elements.emplace_back(vespalib::stringref(dim, label - 1 - dim),
                              vespalib::stringref(label, cur - 1 - label));
    }
}


}



CompactTensorAddress::CompactTensorAddress()
    : _elements()
{
}

CompactTensorAddress::CompactTensorAddress(const Elements &elements_in)
    : _elements(elements_in)
{
}

bool
CompactTensorAddress::hasDimension(const vespalib::string &dimension) const
{
    for (const auto &elem : _elements) {
        if (elem.dimension() == dimension) {
            return true;
        }
    }
    return false;
}

bool
CompactTensorAddress::operator<(const CompactTensorAddress &rhs) const
{
    size_t minSize = std::min(_elements.size(), rhs._elements.size());
    for (size_t i = 0; i < minSize; ++i) {
        if (_elements[i] != rhs._elements[i]) {
            return _elements[i] < rhs._elements[i];
        }
    }
    return _elements.size() < rhs._elements.size();
}

bool
CompactTensorAddress::operator==(const CompactTensorAddress &rhs) const
{
    return _elements == rhs._elements;
}


void
CompactTensorAddress::deserializeFromSparseAddressRef(CompactTensorAddressRef
                                                      ref)
{
    _elements.clear();
    setupElements(_elements, ref);
}


void
CompactTensorAddress::deserializeFromAddressRefV2(CompactTensorAddressRef ref,
                                                  const TensorDimensions &
                                                  dimensions)
{
    _elements.clear();
    SparseTensorAddressDecoder addr(ref);
    for (auto &dim : dimensions) {
        auto label = addr.decodeLabel();
        if (label.size() != 0u) {
            _elements.emplace_back(dim, label);
        }
    }
    assert(!addr.valid());
}



std::ostream &
operator<<(std::ostream &out, const CompactTensorAddress::Elements &elements)
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
operator<<(std::ostream &out, const CompactTensorAddress &value)
{
    out << value.elements();
    return out;
}

} // namespace vespalib::tensor
} // namespace vespalib
