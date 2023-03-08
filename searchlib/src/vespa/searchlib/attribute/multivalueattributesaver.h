// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributesaver.h"
#include "save_utils.h"

namespace search::attribute { class MultiValueMappingBase; }

namespace search {

/*
 * Base class for saving a multivalue attribute (e.g. weighted set of int).
 */
class MultiValueAttributeSaver : public AttributeSaver
{
protected:
    using GenerationHandler = vespalib::GenerationHandler;
    using MvMappingBase = attribute::MultiValueMappingBase;
    attribute::EntryRefVector _frozenIndices;

public:
    MultiValueAttributeSaver(GenerationHandler::Guard &&guard,
                             const attribute::AttributeHeader &header,
                             const MvMappingBase &mvMapping);

    virtual ~MultiValueAttributeSaver();
};


} // namespace search
