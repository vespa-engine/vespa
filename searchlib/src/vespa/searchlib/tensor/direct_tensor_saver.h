// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search::tensor {

class DirectTensorStore;

/*
 * Class for saving a tensor attribute.
 */
class DirectTensorAttributeSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    using GenerationHandler = vespalib::GenerationHandler;

    RefCopyVector _refs;
    const DirectTensorStore &_tensorStore;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    DirectTensorAttributeSaver(GenerationHandler::Guard &&guard,
                               const attribute::AttributeHeader &header,
                               RefCopyVector &&refs,
                               const DirectTensorStore &tensorStore);

    virtual ~DirectTensorAttributeSaver();
};

} // namespace search::tensor
