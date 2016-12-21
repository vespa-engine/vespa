// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_builder.h"
#include <cassert>

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
      _type(eval::ValueType::double_type()),
      _type_made(false)
{
}

SparseTensorBuilder::~SparseTensorBuilder()
{
}


void
SparseTensorBuilder::makeType()
{
    assert(!_type_made);
    assert(_cells.empty());
    std::vector<eval::ValueType::Dimension> dimensions;
    dimensions.reserve(_dimensions.size());
    for (const auto &dim : _dimensions) {
        dimensions.emplace_back(dim);
    }
    _type = (dimensions.empty() ?
             eval::ValueType::double_type() :
             eval::ValueType::tensor_type(std::move(dimensions)));
    _type_made = true;
}


TensorBuilder::Dimension
SparseTensorBuilder::define_dimension(const vespalib::string &dimension)
{
    auto it = _dimensionsEnum.find(dimension);
    if (it != _dimensionsEnum.end()) {
        return it->second;
    }
    assert(!_type_made);
    Dimension res = _dimensionsEnum.size();
    auto insres = _dimensionsEnum.insert(std::make_pair(dimension, res));
    (void) insres;
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
    if (!_type_made) {
        makeType();
    }
    _addressBuilder.buildTo(_normalizedAddressBuilder, _type);
    SparseTensorAddressRef taddress(_normalizedAddressBuilder.getAddressRef());
    // Make a persistent copy of sparse tensor address owned by _stash
    SparseTensorAddressRef address(taddress, _stash);
    _cells[address] = value;
    _addressBuilder.clear();
    _normalizedAddressBuilder.clear();
    return *this;
}


Tensor::UP
SparseTensorBuilder::build()
{
    assert(_addressBuilder.empty());
    if (!_type_made) {
        makeType();
    }
    Tensor::UP ret = std::make_unique<SparseTensor>(std::move(_type),
                                                    std::move(_cells),
                                                    std::move(_stash));
    SparseTensor::Cells().swap(_cells);
    _dimensionsEnum.clear();
    _dimensions.clear();
    _type = eval::ValueType::double_type();
    _type_made = false;
    return ret;
}


} // namespace vespalib::tensor
} // namespace vespalib
