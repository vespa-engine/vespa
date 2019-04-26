// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_mapper.h"
#include "tensor.h"
#include "tensor_visitor.h"
#include "tensor_address_element_iterator.h"
#include "default_tensor.h"
#include "wrapped_simple_tensor.h"
#include <vespa/eval/tensor/sparse/direct_sparse_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_address_mapper.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <limits>

using vespalib::eval::ValueType;
using vespalib::eval::TensorSpec;

namespace vespalib::tensor {

namespace {

//-----------------------------------------------------------------------------

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
SparseTensorMapper<TensorT>::~SparseTensorMapper() = default;

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
    for (const auto &dimension : _builder.fast_type().dimensions()) {
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

//-----------------------------------------------------------------------------

class DenseTensorMapper : public TensorVisitor
{
    ValueType _type;
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

void
DenseTensorMapper::visit(const TensorAddress &address, double value)
{
    uint32_t idx = DenseTensorAddressMapper::mapAddressToIndex(address, _type);
    if (idx != DenseTensorAddressMapper::BAD_ADDRESS) {
        assert(idx < _cells.size());
        _cells[idx] += value;
    }
}

std::unique_ptr<Tensor>
DenseTensorMapper::map(const Tensor &tensor, const ValueType &type)
{
    DenseTensorMapper mapper(type);
    tensor.accept(mapper);
    return mapper.build();
}

//-----------------------------------------------------------------------------

class WrappedTensorMapper : public TensorVisitor
{
    using Label = TensorSpec::Label;

    ValueType  _type;
    TensorSpec _spec;

    WrappedTensorMapper(const ValueType &type)
        : _type(type), _spec(type.to_spec()) {}
    ~WrappedTensorMapper() {}

    void visit(const TensorAddress &address, double value) override;

    std::unique_ptr<Tensor> build() {
        auto tensor = eval::SimpleTensor::create(_spec);
        return std::make_unique<WrappedSimpleTensor>(std::move(tensor));
    }

public:
    static std::unique_ptr<Tensor>
    map(const Tensor &tensor, const ValueType &type);
};

void
WrappedTensorMapper::visit(const TensorAddress &address, double value)
{
    TensorSpec::Address addr;
    TensorAddressElementIterator<TensorAddress> addressIterator(address);
    for (const auto &dimension: _type.dimensions()) {
        if (addressIterator.skipToDimension(dimension.name)) {
            if (dimension.is_indexed()) {
                uint32_t label = DenseTensorAddressMapper::mapLabelToNumber(addressIterator.label());
                if ((label == DenseTensorAddressMapper::BAD_LABEL) || (label >= dimension.size)) {
                    return; // bad address; ignore cell
                }
                addr.emplace(dimension.name, label);
            } else {
                addr.emplace(dimension.name, addressIterator.label());
            }
            addressIterator.next();
        } else {
            if (dimension.is_indexed()) {
                addr.emplace(dimension.name, size_t(0));
            } else {
                addr.emplace(dimension.name, vespalib::string());
            }
        }
    }
    _spec.add(addr, value);
}

std::unique_ptr<Tensor>
WrappedTensorMapper::map(const Tensor &tensor, const ValueType &type)
{
    WrappedTensorMapper mapper(type);
    tensor.accept(mapper);
    return mapper.build();
}

//-----------------------------------------------------------------------------

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
TensorMapper::mapToWrapped(const Tensor &tensor, const ValueType &type)
{
    assert(!type.dimensions().empty());
    return WrappedTensorMapper::map(tensor, type);
}

std::unique_ptr<Tensor>
TensorMapper::map(const Tensor &tensor) const
{
    if (_type.is_sparse()) {
        return mapToSparse<DefaultTensor::type>(tensor, _type);
    } else if (_type.is_dense()) {
        return mapToDense(tensor, _type);
    } else {
        return mapToWrapped(tensor, _type);
    }
}

template
std::unique_ptr<Tensor>
TensorMapper::mapToSparse<SparseTensor>(const Tensor &tensor,
                                           const ValueType &type);

}
