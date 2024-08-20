// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"

namespace config {

class ConfigKey {
public:
    ConfigKey(std::string_view configId,
              std::string_view defName,
              std::string_view defNamespace,
              std::string_view defMd5);

    ConfigKey(std::string_view configId,
              std::string_view defName,
              std::string_view defNamespace,
              std::string_view defMd5,
              const StringVector & defSchema);

    ConfigKey(const ConfigKey &);
    ConfigKey & operator = (const ConfigKey &);
    ConfigKey(ConfigKey &&) noexcept;
    ConfigKey & operator = (ConfigKey &&) noexcept;
    ConfigKey();
    ~ConfigKey();

    bool operator<(const ConfigKey & rhs) const;
    bool operator>(const ConfigKey & rhs) const;
    bool operator==(const ConfigKey & rhs) const;

    const std::string & getDefName() const;
    const std::string & getConfigId() const;
    const std::string & getDefNamespace() const;
    const std::string & getDefMd5() const;
    const StringVector & getDefSchema() const;

    template <typename ConfigType>
    static const ConfigKey create(std::string_view configId)
    {
        return ConfigKey(configId, ConfigType::CONFIG_DEF_NAME,
                                   ConfigType::CONFIG_DEF_NAMESPACE,
                                   ConfigType::CONFIG_DEF_MD5,
                                   ConfigType::CONFIG_DEF_SCHEMA);
    }

    const std::string toString() const;
private:
    std::string _configId;
    std::string _defName;
    std::string _defNamespace;
    std::string _defMd5;
    StringVector     _defSchema;
    std::string _key;
};

} //namespace config
