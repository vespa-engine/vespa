// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_tensor_builder.h"
#include <vespa/vespalib/tensor/tensor.h>

namespace vespalib {
namespace tensor {


SimpleTensorBuilder::SimpleTensorBuilder()
    : TensorBuilder(),
      _addressBuilder(),
      _cells(),
      _dimensionsEnum(),
      _dimensions()
{
}

SimpleTensorBuilder::~SimpleTensorBuilder()
{
}


TensorBuilder::Dimension
SimpleTensorBuilder::define_dimension(const vespalib::string &dimension)
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
SimpleTensorBuilder::add_label(Dimension dimension,
                               const vespalib::string &label)
{
    assert(dimension <= _dimensions.size());
    _addressBuilder.add(_dimensions[dimension], label);
    return *this;
}

TensorBuilder &
SimpleTensorBuilder::add_cell(double value)
{
    _cells[_addressBuilder.build()] = value;
    _addressBuilder.clear();
    return *this;
}


Tensor::UP
SimpleTensorBuilder::build()
{
    SimpleTensor::Dimensions dimensions(_dimensions.begin(), _dimensions.end());
    std::sort(dimensions.begin(), dimensions.end());
    Tensor::UP ret = std::make_unique<SimpleTensor>(std::move(dimensions), std::move(_cells));
    SimpleTensor::Cells().swap(_cells);
    _dimensionsEnum.clear();
    _dimensions.clear();
    return ret;
}


} // namespace vespalib::tensor
} // namespace vespalib
