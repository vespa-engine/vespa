// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dequantizing_tensor_attribute_executor.h"

#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/tensor/tensor_quantization.h>

namespace search::features {

DequantizingTensorAttributeExecutor::DequantizingTensorAttributeExecutor(
    const search::tensor::ITensorAttribute& attribute)
    : _attribute(attribute),
      _dequantizer(attribute.make_dequantizer()),
      _empty_tensor(attribute.getEmptyTensor()),
      _tensor() {
}

DequantizingTensorAttributeExecutor::~DequantizingTensorAttributeExecutor() = default;

void DequantizingTensorAttributeExecutor::execute(const uint32_t doc_id) {
    _tensor = _attribute.getTensor(doc_id);
    if (_tensor) {
        _tensor = _dequantizer->dequantize(*_tensor);
        outputs().set_object(0, *_tensor);
    } else {
        outputs().set_object(0, *_empty_tensor);
    }
}

} // namespace search::features
