// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configsnapshot.h"
#include <vespa/config/common/configvalue.hpp>

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigSnapshot::getConfig(const vespalib::string & configId) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return find(key)->second.second.newInstance<ConfigType>();
}

template <typename ConfigType>
bool
ConfigSnapshot::isChanged(const vespalib::string & configId, int64_t currentGeneration) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return currentGeneration < find(key)->second.first;
}

template <typename ConfigType>
bool
ConfigSnapshot::hasConfig(const vespalib::string & configId) const
{
    ConfigKey key(ConfigKey::create<ConfigType>(configId));
    return (_valueMap.find(key) != _valueMap.end());
}

}
