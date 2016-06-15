// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "iattributesavetarget.h"
#include "tensorattribute.h"

namespace search {

namespace attribute {

/*
 * Class for saving a tensor attribute.
 */
class TensorAttributeSaver : public AttributeSaver
{
public:
    using RefCopyVector = TensorAttribute::RefCopyVector;
private:
    RefCopyVector      _refs;
    const TensorStore &_tensorStore;
    using GenerationHandler = vespalib::GenerationHandler;

    virtual bool onSave(IAttributeSaveTarget &saveTarget) override;
public:
    TensorAttributeSaver(GenerationHandler::Guard &&guard,
                         const IAttributeSaveTarget::Config &cfg,
                         RefCopyVector &&refs,
                         const TensorStore &tensorStore);

    virtual ~TensorAttributeSaver();
};

} // namespace search::attribute

} // namespace search
