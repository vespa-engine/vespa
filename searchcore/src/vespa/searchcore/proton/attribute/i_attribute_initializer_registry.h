// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_initializer.h"

namespace proton {

/**
 * Interface for registering a set of attribute initializers,
 * later to be used to initialize and load the set of attributes.
 */
struct IAttributeInitializerRegistry
{
    virtual ~IAttributeInitializerRegistry() {}
    virtual void add(AttributeInitializer::UP initializer) = 0;
};

} // namespace proton
