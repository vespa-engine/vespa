// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configupdate.h"

namespace config {

ConfigUpdate::ConfigUpdate(const ConfigValue & value, bool changed, int64_t generation)
    : _value(value),
      _hasChanged(changed),
      _generation(generation)
{
}

const ConfigValue & ConfigUpdate::getValue() const { return _value; }
bool ConfigUpdate::hasChanged() const { return _hasChanged; }
int64_t ConfigUpdate::getGeneration() const { return _generation; }

} // namespace config
