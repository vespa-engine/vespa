// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configkey.h"

namespace config {

ConfigKey::ConfigKey(std::string_view configId,
                     std::string_view defName,
                     std::string_view defNamespace,
                     std::string_view defMd5)
    : _configId(configId),
      _defName(defName),
      _defNamespace(defNamespace),
      _defMd5(defMd5),
      _defSchema(),
      _key(_configId + _defName + _defNamespace)
{}

ConfigKey::ConfigKey(std::string_view configId,
                     std::string_view defName,
                     std::string_view defNamespace,
                     std::string_view defMd5,
                     const StringVector & defSchema)
    : _configId(configId),
      _defName(defName),
      _defNamespace(defNamespace),
      _defMd5(defMd5),
      _defSchema(defSchema),
      _key(_configId + _defName + _defNamespace)
{
}

ConfigKey::ConfigKey() = default;
ConfigKey::ConfigKey(const ConfigKey &) = default;
ConfigKey & ConfigKey::operator = (const ConfigKey &) = default;
ConfigKey::ConfigKey(ConfigKey &&) noexcept = default;
ConfigKey & ConfigKey::operator = (ConfigKey &&) noexcept = default;
ConfigKey::~ConfigKey() = default;

bool
ConfigKey::operator<(const ConfigKey & rhs) const
{
    return _key < rhs._key;
}

bool
ConfigKey::operator>(const ConfigKey & rhs) const
{
    return _key > rhs._key;
}

bool
ConfigKey::operator==(const ConfigKey & rhs) const
{
    return _key.compare(rhs._key) == 0;
}

const std::string & ConfigKey::getDefName() const { return _defName; }
const std::string & ConfigKey::getConfigId() const { return _configId; }
const std::string & ConfigKey::getDefNamespace() const { return _defNamespace; }
const std::string & ConfigKey::getDefMd5() const { return _defMd5; }
const StringVector & ConfigKey::getDefSchema() const { return _defSchema; }

const std::string
ConfigKey::toString() const
{
    std::string s;
    s.append("name=");
    s.append(_defNamespace);
    s.append(".");
    s.append(_defName);
    s.append(",configId=");
    s.append(_configId);
    return s;
}

}
