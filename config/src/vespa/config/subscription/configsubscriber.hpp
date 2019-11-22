// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigHandle<ConfigType> >
ConfigSubscriber::subscribe(const std::string & configId, milliseconds timeoutInMillis)
{
    const ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return std::unique_ptr<ConfigHandle<ConfigType> >(new ConfigHandle<ConfigType>(_set.subscribe(key, timeoutInMillis)));
}

}
