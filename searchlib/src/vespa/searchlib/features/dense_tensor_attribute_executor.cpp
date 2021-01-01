// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/i_tensor_attribute.h>

using search::tensor::ITensorAttribute;

namespace search::features {

DenseTensorAttributeExecutor::
DenseTensorAttributeExecutor(const ITensorAttribute& attribute)
    : _attribute(attribute),
      _tensorView(_attribute.getTensorType())
{
}

void
DenseTensorAttributeExecutor::execute(uint32_t docId)
{
    _tensorView.setCells(_attribute.extract_cells_ref(docId));
    outputs().set_object(0, _tensorView);
}

}
