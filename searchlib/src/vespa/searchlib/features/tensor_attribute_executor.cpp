// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/tensor_attribute.h>

using vespalib::eval::TensorValue;

namespace search {
namespace features {

TensorAttributeExecutor::
TensorAttributeExecutor(const search::tensor::TensorAttribute *attribute)
    : _attribute(attribute),
      _emptyTensor(attribute->getEmptyTensor()),
      _tensor(*_emptyTensor)
{
}

void
TensorAttributeExecutor::execute(fef::MatchData &data)
{
    auto tensor = _attribute->getTensor(data.getDocId());
    if (!tensor) {
        _tensor = TensorValue(*_emptyTensor);
    } else {
        _tensor = TensorValue(std::move(tensor));
    }
    outputs().set_object(0, _tensor);
}

} // namespace features
} // namespace search
