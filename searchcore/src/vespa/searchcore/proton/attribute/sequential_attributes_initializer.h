// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributes_initializer_base.h"

namespace proton {

/**
 * Class that initializes and loads a set of attribute vectors in sequence.
 */
class SequentialAttributesInitializer : public AttributesInitializerBase
{
private:
    uint32_t _docIdLimit;

public:
    SequentialAttributesInitializer(uint32_t docIdLimit);
    AttributesVector getInitializedAttributes() const { return _initializedAttributes; }
    virtual void add(AttributeInitializer::UP initializer) override;
};

} // namespace proton
