// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "confighandle.h"
#include <vespa/config/common/configvalue.hpp>

namespace config {

template <typename ConfigType>
ConfigHandle<ConfigType>::ConfigHandle(std::shared_ptr<ConfigSubscription> subscription)
    : _subscription(std::move(subscription))
{
}

template <typename ConfigType>
ConfigHandle<ConfigType>::~ConfigHandle() = default;

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigHandle<ConfigType>::getConfig() const
{
    return _subscription->getConfig().template newInstance<ConfigType>();
}

template <typename ConfigType>
bool
ConfigHandle<ConfigType>::isChanged() const
{
    return _subscription->isChanged();
}

} // namespace config
