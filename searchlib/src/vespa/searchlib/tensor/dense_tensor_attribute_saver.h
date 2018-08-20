// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_attribute.h"
#include <vespa/searchlib/attribute/attributesaver.h>

namespace search::tensor {

class DenseTensorStore;

/*
 * Class for saving a tensor attribute.
 */
class DenseTensorAttributeSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    RefCopyVector      _refs;
    const DenseTensorStore &_tensorStore;
    using GenerationHandler = vespalib::GenerationHandler;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    DenseTensorAttributeSaver(GenerationHandler::Guard &&guard, const attribute::AttributeHeader &header,
                              RefCopyVector &&refs, const DenseTensorStore &tensorStore);

    ~DenseTensorAttributeSaver() override;
};

}
