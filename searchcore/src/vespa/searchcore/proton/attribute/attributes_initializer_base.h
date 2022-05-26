// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_initializer_registry.h"
#include "attribute_initializer_result.h"
#include <vector>

namespace proton {

/**
 * Base class for initialization and loading of a set of attribute vectors.
 */
class AttributesInitializerBase : public IAttributeInitializerRegistry
{
public:
    typedef std::vector<AttributeInitializerResult> AttributesVector;

protected:
    AttributesVector _initializedAttributes;

public:
    static void considerPadAttribute(search::AttributeVector &attribute,
                                     search::SerialNum currentSerialNum,
                                     uint32_t newDocIdLimit);

    AttributesInitializerBase();

};

} // namespace proton

