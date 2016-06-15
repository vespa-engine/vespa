// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigSnapshot::getConfig(const vespalib::string & configId) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    ValueMap::const_iterator it(_valueMap.find(key));
    if (it == _valueMap.end()) {
        throw IllegalConfigKeyException("Unable to find config for key " + key.toString());
    }
    return it->second.second.newInstance<ConfigType>();
}

template <typename ConfigType>
bool
ConfigSnapshot::isChanged(const vespalib::string & configId, int64_t currentGeneration) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    ValueMap::const_iterator it(_valueMap.find(key));
    if (it == _valueMap.end()) {
        throw IllegalConfigKeyException("Unable to find config for key " + key.toString());
    }
    return currentGeneration < it->second.first;
}

template <typename ConfigType>
bool
ConfigSnapshot::hasConfig(const vespalib::string & configId) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return (_valueMap.find(key) != _valueMap.end());
}

}
