// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configdefinition.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/slime/slime.h>

using namespace vespalib;
using namespace vespalib::slime;

namespace config {

ConfigDefinition::ConfigDefinition()
    : _schema()
{}

ConfigDefinition::ConfigDefinition(const std::vector<vespalib::string> & schema)
    : _schema(schema)
{}

void
ConfigDefinition::serialize(Cursor & cursor) const
{
    for (auto it(_schema.begin()), mt(_schema.end()); it != mt; it++) {
        cursor.addString(Memory(*it));
    }
}

void
ConfigDefinition::deserialize(const Inspector & inspector)
{
    for (size_t i(0); i < inspector.entries(); i++) {
        _schema.push_back(inspector[i].asString().make_string());
    }
}

vespalib::string
ConfigDefinition::asString() const
{
    vespalib::asciistream as;
    for (auto it(_schema.begin()), mt(_schema.end()); it != mt; it++) {
        as << *it;
    }
    return as.str();
}

}

