// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::fef {

class Blueprint;

/**
 * This is an interface used during plugin setup to register blueprint
 * prototypes.
 **/
class IBlueprintRegistry
{
public:
    /**
     * Add a blueprint prototype to the registry.
     **/
    virtual void addPrototype(std::shared_ptr<Blueprint> proto) = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IBlueprintRegistry() {}
};

}
