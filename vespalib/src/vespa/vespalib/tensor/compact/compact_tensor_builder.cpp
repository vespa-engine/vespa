// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compact_tensor_builder.h"
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {

CompactTensorBuilder::CompactTensorBuilder()
    : TensorBuilder(),
      _addressBuilder(),
      _normalizedAddressBuilder(),
      _cells(),
      _stash(CompactTensor::STASH_CHUNK_SIZE),
      _dimensionsEnum(),
      _dimensions()
{
}

CompactTensorBuilder::~CompactTensorBuilder()
{
}


TensorBuilder::Dimension
CompactTensorBuilder::define_dimension(const vespalib::string &dimension)
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
CompactTensorBuilder::add_label(Dimension dimension,
                                const vespalib::string &label)
{
    assert(dimension <= _dimensions.size());
    _addressBuilder.add(_dimensions[dimension], label);
    return *this;
}

TensorBuilder &
CompactTensorBuilder::add_cell(double value)
{
    _addressBuilder.buildTo(_normalizedAddressBuilder);
    CompactTensorAddressRef taddress(_normalizedAddressBuilder.getAddressRef());
    // Make a persistent copy of compact tensor address owned by _stash
    CompactTensorAddressRef address(taddress, _stash);
    _cells[address] = value;
    _addressBuilder.clear();
    _normalizedAddressBuilder.clear();
    return *this;
}


Tensor::UP
CompactTensorBuilder::build()
{
    assert(_addressBuilder.empty());
    CompactTensor::Dimensions dimensions(_dimensions.begin(),
                                         _dimensions.end());
    std::sort(dimensions.begin(), dimensions.end());
    Tensor::UP ret = std::make_unique<CompactTensor>(std::move(dimensions),
                                                     std::move(_cells),
                                                     std::move(_stash));
    CompactTensor::Cells().swap(_cells);
    _dimensionsEnum.clear();
    _dimensions.clear();
    return ret;
}


} // namespace vespalib::tensor
} // namespace vespalib
