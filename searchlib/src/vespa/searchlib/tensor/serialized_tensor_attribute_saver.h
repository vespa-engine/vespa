// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search::tensor {

class SerializedTensorStore;

/*
 * Class for saving a tensor attribute.
 */
class SerializedTensorAttributeSaver : public AttributeSaver {
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    RefCopyVector      _refs;
    const SerializedTensorStore &_tensorStore;
    using GenerationHandler = vespalib::GenerationHandler;

    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    SerializedTensorAttributeSaver(GenerationHandler::Guard &&guard,
                                   const attribute::AttributeHeader &header,
                                   RefCopyVector &&refs,
                                   const SerializedTensorStore &tensorStore);

    virtual ~SerializedTensorAttributeSaver();
};

}
