// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "sparse_tensor_dimension_sum.h"
#include "sparse_tensor_address_decoder.h"

namespace vespalib {
namespace tensor {

namespace {

enum class AddressOp
{
    REMOVE,
    COPY
};

using ReduceOps = std::vector<AddressOp>;


ReduceOps
buildReduceOps(const TensorDimensions &dims,
               const vespalib::stringref &dimension)
{
    ReduceOps ops;
    for (auto &dim : dims) {
        if (dim == dimension) {
            ops.push_back(AddressOp::REMOVE);
        } else {
            ops.push_back(AddressOp::COPY);
        }
    }
    return ops;
}


void
reduceAddress(SparseTensorAddressBuilder &builder,
              SparseTensorAddressRef ref,
              const ReduceOps &ops)
{
    builder.clear();
    SparseTensorAddressDecoder addr(ref);
    for (auto op : ops) {
        switch (op) {
        case AddressOp::REMOVE:
            addr.skipLabel();
            break;
        case AddressOp::COPY:
            builder.add(addr.decodeLabel());
            break;
        }
    }
    assert(!addr.valid());
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

SparseTensorDimensionSum::SparseTensorDimensionSum(const TensorImplType &
                                                         tensor,
                                                         const
                                                         vespalib::string &
                                                         dimension)
    : Parent(removeDimension(tensor.dimensions(), dimension))
{
    ReduceOps ops(buildReduceOps(tensor.dimensions(), dimension));
    AddressBuilderType reducedAddress;
    for (const auto &cell : tensor.cells()) {
        reduceAddress(reducedAddress, cell.first, ops);
        _builder.insertCell(reducedAddress, cell.second,
                [](double cellValue, double rhsValue) { return cellValue + rhsValue; });
    }
}


} // namespace vespalib::tensor
} // namespace vespalib
