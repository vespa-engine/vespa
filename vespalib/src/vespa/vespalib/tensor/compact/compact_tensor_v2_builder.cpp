// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compact_tensor_v2_builder.h"
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {

CompactTensorV2Builder::CompactTensorV2Builder()
    : TensorBuilder(),
      _addressBuilder(),
      _normalizedAddressBuilder(),
      _cells(),
      _stash(CompactTensorV2::STASH_CHUNK_SIZE),
      _dimensionsEnum(),
      _dimensions(),
      _sortedDimensions()
{
}

CompactTensorV2Builder::~CompactTensorV2Builder()
{
}


void
CompactTensorV2Builder::makeSortedDimensions()
{
    assert(_sortedDimensions.empty());
    assert(_cells.empty());
    _sortedDimensions = _dimensions;
    std::sort(_sortedDimensions.begin(), _sortedDimensions.end());
}


TensorBuilder::Dimension
CompactTensorV2Builder::define_dimension(const vespalib::string &dimension)
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
CompactTensorV2Builder::add_label(Dimension dimension,
                                const vespalib::string &label)
{
    assert(dimension <= _dimensions.size());
    _addressBuilder.add(_dimensions[dimension], label);
    return *this;
}

TensorBuilder &
CompactTensorV2Builder::add_cell(double value)
{
    if (_dimensions.size() != _sortedDimensions.size()) {
        makeSortedDimensions();
    }
    _addressBuilder.buildTo(_normalizedAddressBuilder, _sortedDimensions);
    CompactTensorAddressRef taddress(_normalizedAddressBuilder.getAddressRef());
    // Make a persistent copy of compact tensor address owned by _stash
    CompactTensorAddressRef address(taddress, _stash);
    _cells[address] = value;
    _addressBuilder.clear();
    _normalizedAddressBuilder.clear();
    return *this;
}


Tensor::UP
CompactTensorV2Builder::build()
{
    assert(_addressBuilder.empty());
    if (_dimensions.size() != _sortedDimensions.size()) {
        makeSortedDimensions();
    }
    CompactTensorV2::Dimensions dimensions(_sortedDimensions.begin(),
                                           _sortedDimensions.end());
    Tensor::UP ret = std::make_unique<CompactTensorV2>(std::move(dimensions),
                                                       std::move(_cells),
                                                       std::move(_stash));
    CompactTensorV2::Cells().swap(_cells);
    _dimensionsEnum.clear();
    _dimensions.clear();
    _sortedDimensions.clear();
    return ret;
}


} // namespace vespalib::tensor
} // namespace vespalib
