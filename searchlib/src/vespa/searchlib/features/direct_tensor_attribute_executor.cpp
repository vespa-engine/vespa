// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "direct_tensor_attribute_executor.h"
#include <vespa/searchlib/tensor/i_tensor_attribute.h>

namespace search::features {

DirectTensorAttributeExecutor::
DirectTensorAttributeExecutor(const ITensorAttribute &attribute)
    : _attribute(attribute)
{
}

void
DirectTensorAttributeExecutor::execute(uint32_t docId)
{
    outputs().set_object(0, _attribute.get_tensor_ref(docId));
}

}
