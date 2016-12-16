// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_mapper.h"
#include "tensor.h"
#include "tensor_visitor.h"
#include <vespa/vespalib/tensor/sparse/direct_sparse_tensor_builder.h>
#include <vespa/vespalib/tensor/dense/dense_tensor.h>
#include "tensor_address_element_iterator.h"
#include "default_tensor.h"

using vespalib::eval::ValueType;

namespace vespalib {
namespace tensor {

namespace {

template <class TensorT>
class SparseTensorMapper : public TensorVisitor
{
    using Builder = DirectTensorBuilder<TensorT>;
    using AddressBuilderType = typename Builder::AddressBuilderType;

    Builder _builder;
    AddressBuilderType _addressBuilder;

    void mapAddress(const TensorAddress &address);
    virtual void visit(const TensorAddress &address, double value) override;

    SparseTensorMapper(const ValueType &type);

    ~SparseTensorMapper();

    std::unique_ptr<Tensor> build();
public:
    static std::unique_ptr<Tensor>
    map(const Tensor &tensor, const ValueType &type);
};

template <class TensorT>
SparseTensorMapper<TensorT>::
SparseTensorMapper(const ValueType &type)
    : TensorVisitor(),
      _builder(type),
      _addressBuilder()
{
}

template <class TensorT>
SparseTensorMapper<TensorT>::~SparseTensorMapper()
{
}

template <class TensorT>
std::unique_ptr<Tensor>
SparseTensorMapper<TensorT>::build()
{
    return _builder.build();
}

template <>
void
SparseTensorMapper<SparseTensor>::
mapAddress(const TensorAddress &address)
{
    _addressBuilder.clear();
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    for (const auto &dimension : _builder.type().dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            _addressBuilder.add(addressIterator.label());
            addressIterator.next();
        } else {
            // output dimension not in input
            _addressBuilder.addUndefined();
        }
    }
}

template <class TensorT>
void
SparseTensorMapper<TensorT>::visit(const TensorAddress &address, double value)
{
    mapAddress(address);
    _builder.insertCell(_addressBuilder, value,
                        [](double oldValue, double newValue)
                        { return oldValue + newValue; });
}

template <class TensorT>
std::unique_ptr<Tensor>
SparseTensorMapper<TensorT>::map(const Tensor &tensor,
                                 const ValueType &type)
{
    SparseTensorMapper<TensorT> mapper(type);
    tensor.accept(mapper);
    return mapper.build();
}

static constexpr uint32_t BAD_LABEL = std::numeric_limits<uint32_t>::max();
static constexpr uint32_t BAD_ADDRESS = std::numeric_limits<uint32_t>::max();

uint32_t mapLabelToNumber(vespalib::stringref label) {
    uint32_t result = 0;
    for (char c : label) {
        if (c < '0' || c > '9') {
            return BAD_LABEL; // bad char
        }
        result = result * 10 + (c - '0');
        if (result > 100000000) {
            return BAD_LABEL; // overflow
        }
    }
    return result;
}

class DenseTensorTypeMapper : public TensorVisitor
{
    ValueType _type;
    std::vector<ValueType::Dimension> _dimensions;

    bool addressOK(const TensorAddress &address);
    void expandUnboundDimensions(const TensorAddress &address);

    virtual void visit(const TensorAddress &address, double value) override;

    DenseTensorTypeMapper(const ValueType &type);
    ~DenseTensorTypeMapper();

    ValueType build();
public:
    static ValueType map(const Tensor &tensor, const ValueType &type);
};

bool
DenseTensorTypeMapper::addressOK(const TensorAddress &address)
{
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    auto dimIterator = _dimensions.begin();
    for (const auto &dimension : _type.dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            uint32_t label = mapLabelToNumber(addressIterator.label());
            if (label == BAD_LABEL ||
                (dimension.is_bound() && label >= dimIterator->size)) {
                return false;
            }
            addressIterator.next();
        }
        ++dimIterator;
    }
    assert(dimIterator == _dimensions.end());
    return true;
}


void
DenseTensorTypeMapper::expandUnboundDimensions(const TensorAddress &address)
{
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    auto dimIterator = _dimensions.begin();
    for (const auto &dimension : _type.dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            uint32_t label = mapLabelToNumber(addressIterator.label());
            if (label != BAD_LABEL &&
                !dimension.is_bound() &&
                label >= dimIterator->size) {
                dimIterator->size = label + 1;
            }
            addressIterator.next();
        }
        ++dimIterator;
    }
    assert(dimIterator == _dimensions.end());
}

void
DenseTensorTypeMapper::visit(const TensorAddress &address, double value)
{
    (void) value;
    if (addressOK(address)) {
        expandUnboundDimensions(address);
    }
}

DenseTensorTypeMapper::DenseTensorTypeMapper(const ValueType &type)
    : _type(type),
      _dimensions(type.dimensions())
{
    for (auto &dimension : _dimensions) {
        if (!dimension.is_bound())
            dimension.size = 1;
    }
}

DenseTensorTypeMapper::~DenseTensorTypeMapper()
{
}

ValueType
DenseTensorTypeMapper::build()
{
    return ValueType::tensor_type(std::move(_dimensions));
}

ValueType
DenseTensorTypeMapper::map(const Tensor &tensor, const ValueType &type)
{
    DenseTensorTypeMapper mapper(type);
    tensor.accept(mapper);
    return mapper.build();
}

class DenseTensorMapper : public TensorVisitor
{
    eval::ValueType _type;
    DenseTensor::Cells _cells;

    uint32_t mapAddressToIndex(const TensorAddress &address);
    virtual void visit(const TensorAddress &address, double value) override;

    DenseTensorMapper(const ValueType &type);
    ~DenseTensorMapper();

    std::unique_ptr<Tensor> build();
public:
    static std::unique_ptr<Tensor>
    map(const Tensor &tensor, const ValueType &type);
};

DenseTensorMapper::DenseTensorMapper(const ValueType &type)
    : _type(type),
      _cells()
{
    size_t size = 1;
    for (const auto &dimension : type.dimensions()) {
        size *= dimension.size;
    }
    _cells.resize(size);
}

DenseTensorMapper::~DenseTensorMapper()
{
}

std::unique_ptr<Tensor>
DenseTensorMapper::build()
{
    return std::make_unique<DenseTensor>(std::move(_type),
                                         std::move(_cells));
}

uint32_t
DenseTensorMapper::mapAddressToIndex(const TensorAddress &address)
{
    uint32_t idx = 0;
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    for (const auto &dimension : _type.dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            uint32_t label = mapLabelToNumber(addressIterator.label());
            if (label == BAD_LABEL || label >= dimension.size) {
                return BAD_ADDRESS;
            }
            idx = idx * dimension.size + label;
            addressIterator.next();
        } else {
            // output dimension not in input
            idx = idx * dimension.size;
        }
    }
    return idx;
}

void
DenseTensorMapper::visit(const TensorAddress &address, double value)
{
    uint32_t idx = mapAddressToIndex(address);
    if (idx != BAD_ADDRESS) {
        assert(idx < _cells.size());
        _cells[idx] += value;
    }
}

std::unique_ptr<Tensor>
DenseTensorMapper::map(const Tensor &tensor, const ValueType &type)
{
    DenseTensorMapper mapper(type.is_abstract() ?
                             DenseTensorTypeMapper::map(tensor, type) :
                             type);
    tensor.accept(mapper);
    return mapper.build();
}

} // namespace vespalib::tensor::<anonymous>

TensorMapper::TensorMapper(const ValueType &type)
      : _type(type)
{
}

TensorMapper::~TensorMapper()
{
}

template <typename TensorT>
std::unique_ptr<Tensor>
TensorMapper::mapToSparse(const Tensor &tensor, const ValueType &type)
{
    assert(type.is_sparse());
    return SparseTensorMapper<TensorT>::map(tensor, type);
}

std::unique_ptr<Tensor>
TensorMapper::mapToDense(const Tensor &tensor, const ValueType &type)
{
    assert(type.is_dense());
    return DenseTensorMapper::map(tensor, type);
}

std::unique_ptr<Tensor>
TensorMapper::map(const Tensor &tensor) const
{
    if (_type.is_sparse()) {
        return mapToSparse<DefaultTensor::type>(tensor, _type);
    } else if (_type.is_dense()) {
        return mapToDense(tensor, _type);
    } else {
        return std::unique_ptr<Tensor>();
    }
}

template
std::unique_ptr<Tensor>
TensorMapper::mapToSparse<SparseTensor>(const Tensor &tensor,
                                           const ValueType &type);

} // namespace vespalib::tensor
} // namespace vespalib
