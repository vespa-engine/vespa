// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/confighandle.h>

namespace config {

class IHandle
{
public:
    typedef std::unique_ptr<IHandle> UP;
    virtual std::unique_ptr<const ConfigInstance> getConfig() = 0;
    virtual bool isChanged() = 0;
    virtual ~IHandle() { }
};

template <typename ConfigType>
class GenericHandle : public IHandle
{
public:
    GenericHandle(std::unique_ptr<ConfigHandle<ConfigType> > handle)
        : _handle(std::move(handle))
    {
    }

    std::unique_ptr<const ConfigInstance> getConfig() override {
        return std::unique_ptr<const ConfigInstance>(_handle->getConfig().release());
    }
    bool isChanged() override { return _handle->isChanged(); }
private:
    std::unique_ptr<ConfigHandle <ConfigType> > _handle;
};

}

