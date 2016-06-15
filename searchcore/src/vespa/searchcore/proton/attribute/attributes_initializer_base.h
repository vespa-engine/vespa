// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_initializer_registry.h"
#include <vespa/searchlib/attribute/attributevector.h>

namespace proton {

/**
 * Base class for initialization and loading of a set of attribute vectors.
 */
class AttributesInitializerBase : public IAttributeInitializerRegistry
{
public:
    typedef std::vector<search::AttributeVector::SP> AttributesVector;

protected:
    AttributesVector _initializedAttributes;

public:
    static void considerPadAttribute(search::AttributeVector &attribute,
                                     search::SerialNum currentSerialNum,
                                     uint32_t newDocIdLimit);

    AttributesInitializerBase();

};

} // namespace proton

