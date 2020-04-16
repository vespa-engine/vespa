// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

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
    ConfigDefinition(const std::vector<vespalib::string> & schema);
    void deserialize(const vespalib::slime::Inspector & inspector);
    void serialize(vespalib::slime::Cursor & cursor) const;
    vespalib::string asString() const;
private:
    std::vector<vespalib::string> _schema;
};

} //namespace config

