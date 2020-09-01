// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>

namespace search {
namespace features {

TensorAttributeExecutor::
TensorAttributeExecutor(const search::tensor::ITensorAttribute *attribute)
    : _attribute(attribute),
      _emptyTensor(attribute->getEmptyTensor()),
      _tensor()
{
}

void
TensorAttributeExecutor::execute(uint32_t docId)
{
    using DTA = search::tensor::DirectTensorAttribute;
    if (auto direct = dynamic_cast<const DTA *>(_attribute)) {
        outputs().set_object(0, direct->get_tensor_ref(docId));
        return;
    }
    _tensor = _attribute->getTensor(docId);
    if (_tensor) {
        outputs().set_object(0, *_tensor);
    } else {
        outputs().set_object(0, *_emptyTensor);
    }
}

} // namespace features
} // namespace search
