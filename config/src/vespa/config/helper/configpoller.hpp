// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configpoller.h"
#include <vespa/config/subscription/configsubscriber.hpp>

namespace config {

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

template <typename ConfigType>
void
ConfigPoller::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, vespalib::duration subscribeTimeout)
{
    std::unique_ptr<ConfigHandle<ConfigType> > handle(_subscriber->subscribe<ConfigType>(configId, subscribeTimeout));
    _handleList.emplace_back(std::make_unique<GenericHandle<ConfigType>>(std::move(handle)));
    _callbackList.push_back(callback);
}

} // namespace config
