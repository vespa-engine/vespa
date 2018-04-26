// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_tensor_attribute_vector_read_guard.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/eval/tensor/tensor.h>

namespace search::tensor {

using vespalib::tensor::Tensor;

namespace {

const ITensorAttribute &
getTensorAttribute(const search::attribute::IAttributeVector &attr)
{
    const ITensorAttribute *result = attr.asTensorAttribute();
    assert(result != nullptr);
    return *result;
}

}

ImportedTensorAttributeVectorReadGuard::ImportedTensorAttributeVectorReadGuard(const attribute::ImportedAttributeVector &imported_attribute,
                                                                               bool stableEnumGuard)
    : ImportedAttributeVectorReadGuard(imported_attribute,
                                       stableEnumGuard),
      _target_tensor_attribute(getTensorAttribute(_target_attribute))
{
}

ImportedTensorAttributeVectorReadGuard::~ImportedTensorAttributeVectorReadGuard()
{
}

const ITensorAttribute *
ImportedTensorAttributeVectorReadGuard::asTensorAttribute() const
{
    return this;
}

std::unique_ptr<Tensor>
ImportedTensorAttributeVectorReadGuard::getTensor(uint32_t docId) const
{
    return _target_tensor_attribute.getTensor(getTargetLid(docId));
}

std::unique_ptr<Tensor>
ImportedTensorAttributeVectorReadGuard::getEmptyTensor() const
{
    return _target_tensor_attribute.getEmptyTensor();
}

void
ImportedTensorAttributeVectorReadGuard::getTensor(uint32_t docId, vespalib::tensor::MutableDenseTensorView &tensor) const
{
    _target_tensor_attribute.getTensor(getTargetLid(docId), tensor);
}

vespalib::eval::ValueType
ImportedTensorAttributeVectorReadGuard::getTensorType() const
{
    return _target_tensor_attribute.getTensorType();
}

}
