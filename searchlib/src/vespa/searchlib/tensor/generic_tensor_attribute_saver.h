// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search {

namespace tensor {

class GenericTensorStore;

/*
 * Class for saving a tensor attribute.
 */
class GenericTensorAttributeSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    RefCopyVector      _refs;
    const GenericTensorStore &_tensorStore;
    using GenerationHandler = vespalib::GenerationHandler;

    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    GenericTensorAttributeSaver(GenerationHandler::Guard &&guard,
                                const attribute::AttributeHeader &header,
                                RefCopyVector &&refs,
                                const GenericTensorStore &tensorStore);

    virtual ~GenericTensorAttributeSaver();
};

} // namespace search::tensor

} // namespace search
