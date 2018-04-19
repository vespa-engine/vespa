// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_tensor_attribute_vector_read_guard.h"
#include <vespa/eval/tensor/tensor.h>

namespace search::tensor {

using vespalib::tensor::Tensor;

ImportedTensorAttributeVectorReadGuard::ImportedTensorAttributeVectorReadGuard(const attribute::ImportedAttributeVector &imported_attribute,
                                                                               bool stableEnumGuard)
    : ImportedAttributeVectorReadGuard(imported_attribute,
                                       stableEnumGuard),
      _target_tensor_attribute(dynamic_cast<const ITensorAttribute &>(*imported_attribute.getTargetAttribute()))
{
}

ImportedTensorAttributeVectorReadGuard::~ImportedTensorAttributeVectorReadGuard()
{
}

std::unique_ptr<Tensor>
ImportedTensorAttributeVectorReadGuard::getTensor(uint32_t docId) const
{
    return _target_tensor_attribute.getTensor(getReferencedLid(docId));
}

std::unique_ptr<Tensor>
ImportedTensorAttributeVectorReadGuard::getEmptyTensor() const
{
    return _target_tensor_attribute.getEmptyTensor();
}

void
ImportedTensorAttributeVectorReadGuard::getTensor(uint32_t docId, vespalib::tensor::MutableDenseTensorView &tensor) const
{
    _target_tensor_attribute.getTensor(getReferencedLid(docId), tensor);
}

vespalib::eval::ValueType
ImportedTensorAttributeVectorReadGuard::getTensorType() const
{
    return _target_tensor_attribute.getTensorType();
}

}
