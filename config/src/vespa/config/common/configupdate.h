// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configvalue.h"

namespace config {

/**
 * A config update contains a config value, and metadata saying if the value is
 * changed or not.
 */
class ConfigUpdate
{
public:
    ConfigUpdate(ConfigValue value, bool changed, int64_t generation);
    ConfigUpdate(const ConfigUpdate &) = delete;
    ConfigUpdate & operator = (const ConfigUpdate &) = delete;
    ~ConfigUpdate();
    const ConfigValue & getValue() const;
    bool hasChanged() const;
    int64_t getGeneration() const;
    void merge(const ConfigUpdate & b) { _hasChanged = _hasChanged || b.hasChanged(); }
private:
    ConfigValue _value;
    bool        _hasChanged;
    int64_t     _generation;
};

} // namespace config

