// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor_unsorted_address_builder.h"
#include "sparse_tensor_address_builder.h"
#include <algorithm>

namespace vespalib {
namespace tensor {

CompactTensorUnsortedAddressBuilder::CompactTensorUnsortedAddressBuilder()
    : _elementStrings(),
      _elements()
{
}


void
CompactTensorUnsortedAddressBuilder::buildTo(SparseTensorAddressBuilder &
                                             builder,
                                             const TensorDimensions &
                                             dimensions)
{
    const char *base = &_elementStrings[0];
    std::sort(_elements.begin(), _elements.end(),
              [=](const ElementRef &lhs, const ElementRef &rhs)
              { return lhs.getDimension(base) < rhs.getDimension(base); });
    // build normalized address with sorted dimensions
    auto dimsItr = dimensions.cbegin();
    auto dimsItrEnd = dimensions.cend();
    for (const auto &element : _elements) {
        while ((dimsItr != dimsItrEnd) &&
               (*dimsItr < element.getDimension(base))) {
            builder.addUndefined();
            ++dimsItr;
        }
        assert((dimsItr != dimsItrEnd) &&
               (*dimsItr == element.getDimension(base)));
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
