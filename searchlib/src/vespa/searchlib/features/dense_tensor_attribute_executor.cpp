// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>

using search::tensor::DenseTensorAttribute;
using vespalib::eval::Tensor;
using vespalib::eval::TensorValue;
using vespalib::tensor::MutableDenseTensorView;

namespace search {
namespace features {

DenseTensorAttributeExecutor::
DenseTensorAttributeExecutor(const DenseTensorAttribute *attribute)
    : _attribute(attribute),
      _tensorView(_attribute->getConfig().tensorType()),
      _tensor(_tensorView)
{
}

void
DenseTensorAttributeExecutor::execute(uint32_t docId)
{
    _attribute->getTensor(docId, _tensorView);
    outputs().set_object(0, _tensor);
}

} // namespace features
} // namespace search
