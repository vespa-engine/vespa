// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "iattributesavetarget.h"
#include "multi_value_mapping2.h"

namespace search {

/*
 * Base class for saving a multivalue attribute (e.g. weighted set of int).
 */
class MultiValueAttributeSaver : public AttributeSaver
{
protected:
    using GenerationHandler = vespalib::GenerationHandler;
    using MvMappingBase = attribute::MultiValueMapping2Base;
    using RefCopyVector = MvMappingBase::RefCopyVector;
    RefCopyVector _frozenIndices;

public:
    MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                             const IAttributeSaveTarget::Config &cfg,
                             const MvMappingBase &mvMapping);

    virtual ~MultiValueAttributeSaver();
};


} // namespace search
