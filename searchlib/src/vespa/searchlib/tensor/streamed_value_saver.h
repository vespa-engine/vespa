// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search::tensor {

class StreamedValueStore;

/*
 * Class for saving a tensor attribute.
 */
class StreamedValueSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    using GenerationHandler = vespalib::GenerationHandler;

    RefCopyVector _refs;
    const StreamedValueStore &_tensorStore;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    StreamedValueSaver(GenerationHandler::Guard &&guard,
                       const attribute::AttributeHeader &header,
                       RefCopyVector &&refs,
                       const StreamedValueStore &tensorStore);

    virtual ~StreamedValueSaver();
};

} // namespace search::tensor
