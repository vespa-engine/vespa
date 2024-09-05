// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configkey.h>
#include <set>
#include <string>

namespace config {

/**
 * A ConfigKeySet is a set of ConfigKey objects. Each ConfigKey represents a
 * config by its definition name, version, md5, namespace and config id.
 */
class ConfigKeySet : public std::set<ConfigKey>
{
public:
    /**
     * Add a new config type with a config id to this set.
     *
     * @param configId the configId of this key.
     * @return *this for chaining.
     */
    template <typename... ConfigTypes>
    ConfigKeySet & add(const std::string & configId);

    /**
     * Add add another key set to this set.
     *
     * @param configKeySet The set to add.
     * @return *this for chaining.
     */
    ConfigKeySet & add(const ConfigKeySet & configKeySet);
private:
    template<typename ConfigType, typename... ConfigTypes>
    void addImpl(const std::string & configId);
};

} // namespace config

#include "configkeyset.hpp"

