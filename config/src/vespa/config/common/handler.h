// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "configupdate.h"

namespace config {

/**
 * A Handler is a component to whom you can pass an object.
 **/
template <typename T>
struct Handler
{
    virtual void handle(std::unique_ptr<T> obj) = 0;
    virtual ~Handler() {}
};

typedef Handler<ConfigUpdate> ConfigHandler;

} // namespace config

