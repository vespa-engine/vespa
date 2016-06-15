// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_tensor_dimension_sum.h"
#include <vespa/vespalib/tensor/tensor_address_element_iterator.h>

namespace vespalib {
namespace tensor {

namespace {

template <class AddressBuilder, class Address>
void
removeDimension(AddressBuilder &addressBuilder,
                const Address &address,
                const vespalib::stringref dimension)
{
    addressBuilder.clear();
    for (const auto &elem : address.elements()) {
        if (elem.dimension() != dimension) {
            addressBuilder.add(elem.dimension(), elem.label());
        }
    }
}

TensorDimensions
removeDimension(const TensorDimensions &dimensions,
                const vespalib::string &dimension)
{
    TensorDimensions result = dimensions;
    auto itr = std::lower_bound(result.begin(), result.end(), dimension);
    if (itr != result.end() && *itr == dimension) {
        result.erase(itr);
    }
    return result;
}

}

SimpleTensorDimensionSum::SimpleTensorDimensionSum(const TensorImplType &tensor,
                                                   const vespalib::string &
                                                   dimension)
    : Parent(removeDimension(tensor.dimensions(), dimension))
{
    AddressBuilderType reducedAddress;
    for (const auto &cell : tensor.cells()) {
        removeDimension<AddressBuilderType, AddressType>
            (reducedAddress, cell.first, dimension);
        _builder.insertCell(reducedAddress, cell.second,
                [](double cellValue, double rhsValue) { return cellValue + rhsValue; });
    }
}


} // namespace vespalib::tensor
} // namespace vespalib
