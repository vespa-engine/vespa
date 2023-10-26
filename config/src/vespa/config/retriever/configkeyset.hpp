// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {


template <typename... ConfigTypes>
ConfigKeySet &
ConfigKeySet::add(const vespalib::string & configId)
{
    addImpl<ConfigTypes...>(configId);
    return *this;
}

template <typename ConfigType, typename... ConfigTypes>
void
ConfigKeySet::addImpl(const vespalib::string & configId)
{
    insert(ConfigKey::create<ConfigType>(configId));
    if constexpr(sizeof...(ConfigTypes) > 0) {
        addImpl<ConfigTypes...>(configId);
    }
}

}
