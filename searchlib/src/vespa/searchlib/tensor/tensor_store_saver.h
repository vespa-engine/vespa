// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributesaver.h>
#include "tensor_attribute.h"

namespace search::tensor {

/*
 * Class for saving a tensor attribute.
 */
class TensorStoreSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    using GenerationHandler = vespalib::GenerationHandler;

    RefCopyVector _refs;
    const TensorStore& _tensorStore;

    bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    TensorStoreSaver(GenerationHandler::Guard &&guard,
                     const attribute::AttributeHeader &header,
                     RefCopyVector &&refs,
                     const TensorStore &tensorStore);

    virtual ~TensorStoreSaver();
};

} // namespace search::tensor
