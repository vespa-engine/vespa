// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"

namespace vespalib::slime {
    struct Cursor;
    struct Inspector;
}
namespace config {

/**
 * Represents a config definition.
 */
class ConfigDefinition {
public:
    ConfigDefinition();
    ConfigDefinition(StringVector schema);
    void deserialize(const vespalib::slime::Inspector & inspector);
    void serialize(vespalib::slime::Cursor & cursor) const;
    vespalib::string asString() const;
private:
    StringVector _schema;
};

} //namespace config

