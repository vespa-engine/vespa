// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_builder.h"
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {

SparseTensorBuilder::SparseTensorBuilder()
    : TensorBuilder(),
      _addressBuilder(),
      _normalizedAddressBuilder(),
      _cells(),
      _stash(SparseTensor::STASH_CHUNK_SIZE),
      _dimensionsEnum(),
      _dimensions(),
      _sortedDimensions()
{
}

SparseTensorBuilder::~SparseTensorBuilder()
{
}


void
SparseTensorBuilder::makeSortedDimensions()
{
    assert(_sortedDimensions.empty());
    assert(_cells.empty());
    _sortedDimensions = _dimensions;
    std::sort(_sortedDimensions.begin(), _sortedDimensions.end());
}


TensorBuilder::Dimension
SparseTensorBuilder::define_dimension(const vespalib::string &dimension)
{
    auto it = _dimensionsEnum.find(dimension);
    if (it != _dimensionsEnum.end()) {
        return it->second;
    }
    Dimension res = _dimensionsEnum.size();
    auto insres = _dimensionsEnum.insert(std::make_pair(dimension, res));
    assert(insres.second);
    assert(insres.first->second == res);
    assert(_dimensions.size() == res);
    _dimensions.push_back(dimension);
    return res;
}

TensorBuilder &
SparseTensorBuilder::add_label(Dimension dimension,
                                const vespalib::string &label)
{
    assert(dimension <= _dimensions.size());
    _addressBuilder.add(_dimensions[dimension], label);
    return *this;
}

TensorBuilder &
SparseTensorBuilder::add_cell(double value)
{
    if (_dimensions.size() != _sortedDimensions.size()) {
        makeSortedDimensions();
    }
    _addressBuilder.buildTo(_normalizedAddressBuilder, _sortedDimensions);
    CompactTensorAddressRef taddress(_normalizedAddressBuilder.getAddressRef());
    // Make a persistent copy of sparse tensor address owned by _stash
    CompactTensorAddressRef address(taddress, _stash);
    _cells[address] = value;
    _addressBuilder.clear();
    _normalizedAddressBuilder.clear();
    return *this;
}


Tensor::UP
SparseTensorBuilder::build()
{
    assert(_addressBuilder.empty());
    if (_dimensions.size() != _sortedDimensions.size()) {
        makeSortedDimensions();
    }
    SparseTensor::Dimensions dimensions(_sortedDimensions.begin(),
                                           _sortedDimensions.end());
    Tensor::UP ret = std::make_unique<SparseTensor>(std::move(dimensions),
                                                       std::move(_cells),
                                                       std::move(_stash));
    SparseTensor::Cells().swap(_cells);
    _dimensionsEnum.clear();
    _dimensions.clear();
    _sortedDimensions.clear();
    return ret;
}


} // namespace vespalib::tensor
} // namespace vespalib
