// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {


template <typename... ConfigTypes>
ConfigKeySet &
ConfigKeySet::add(const vespalib::string & configId)
{
    addImpl(configId, TypeTag<ConfigTypes...>());
    return *this;
}

template <typename ConfigType>
void
ConfigKeySet::addImpl(const vespalib::string & configId, TypeTag<ConfigType>)
{
    insert(ConfigKey::create<ConfigType>(configId));
}

template <typename ConfigType, typename... ConfigTypes>
void
ConfigKeySet::addImpl(const vespalib::string & configId, TypeTag<ConfigType, ConfigTypes...>)
{
    insert(ConfigKey::create<ConfigType>(configId));
    addImpl(configId, TypeTag<ConfigTypes...>());
}


}
