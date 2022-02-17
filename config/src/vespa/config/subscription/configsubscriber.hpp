// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscriber.h"
#include "confighandle.hpp"

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigHandle<ConfigType> >
ConfigSubscriber::subscribe(const std::string & configId, vespalib::duration timeout)
{
    const ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return std::make_unique<ConfigHandle<ConfigType> >(_set.subscribe(key, timeout));
}

}
