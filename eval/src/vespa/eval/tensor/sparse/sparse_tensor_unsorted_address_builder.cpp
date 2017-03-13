// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_unsorted_address_builder.h"
#include "sparse_tensor_address_builder.h"
#include <vespa/eval/eval/value_type.h>
#include <algorithm>

namespace vespalib {
namespace tensor {

SparseTensorUnsortedAddressBuilder::SparseTensorUnsortedAddressBuilder()
    : _elementStrings(),
      _elements()
{
}

SparseTensorUnsortedAddressBuilder::~SparseTensorUnsortedAddressBuilder() {
}

void
SparseTensorUnsortedAddressBuilder::buildTo(SparseTensorAddressBuilder &
                                            builder,
                                            const eval::ValueType &type)
{
    const char *base = &_elementStrings[0];
    std::sort(_elements.begin(), _elements.end(),
              [=](const ElementRef &lhs, const ElementRef &rhs)
              { return lhs.getDimension(base) < rhs.getDimension(base); });
    // build normalized address with sorted dimensions
    auto dimsItr = type.dimensions().cbegin();
    auto dimsItrEnd = type.dimensions().cend();
    for (const auto &element : _elements) {
        while ((dimsItr != dimsItrEnd) &&
               (dimsItr->name < element.getDimension(base))) {
            builder.addUndefined();
            ++dimsItr;
        }
        assert((dimsItr != dimsItrEnd) &&
               (dimsItr->name == element.getDimension(base)));
        builder.add(element.getLabel(base));
        ++dimsItr;
    }
    while (dimsItr != dimsItrEnd) {
        builder.addUndefined();
        ++dimsItr;
    }
}


} // namespace vespalib::tensor
} // namespace vespalib
