// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"

namespace config {

class ConfigKey {
public:
    ConfigKey(vespalib::stringref configId,
              vespalib::stringref defName,
              vespalib::stringref defNamespace,
              vespalib::stringref defMd5);

    ConfigKey(vespalib::stringref configId,
              vespalib::stringref defName,
              vespalib::stringref defNamespace,
              vespalib::stringref defMd5,
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

    const vespalib::string & getDefName() const;
    const vespalib::string & getConfigId() const;
    const vespalib::string & getDefNamespace() const;
    const vespalib::string & getDefMd5() const;
    const StringVector & getDefSchema() const;

    template <typename ConfigType>
    static const ConfigKey create(vespalib::stringref configId)
    {
        return ConfigKey(configId, ConfigType::CONFIG_DEF_NAME,
                                   ConfigType::CONFIG_DEF_NAMESPACE,
                                   ConfigType::CONFIG_DEF_MD5,
                                   ConfigType::CONFIG_DEF_SCHEMA);
    }

    const vespalib::string toString() const;
private:
    vespalib::string _configId;
    vespalib::string _defName;
    vespalib::string _defNamespace;
    vespalib::string _defMd5;
    StringVector     _defSchema;
    vespalib::string _key;
};

} //namespace config
