// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace config {

class ConfigInstance;

class IHandle
{
public:
    typedef std::unique_ptr<IHandle> UP;
    virtual std::unique_ptr<const ConfigInstance> getConfig() = 0;
    virtual bool isChanged() = 0;
    virtual ~IHandle() = default;
};

}

