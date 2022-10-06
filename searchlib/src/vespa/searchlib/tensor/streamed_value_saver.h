// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search::tensor {

class TensorBufferStore;

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
    const TensorBufferStore &_tensorStore;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    StreamedValueSaver(GenerationHandler::Guard &&guard,
                       const attribute::AttributeHeader &header,
                       RefCopyVector &&refs,
                       const TensorBufferStore &tensorStore);

    virtual ~StreamedValueSaver();
};

} // namespace search::tensor
