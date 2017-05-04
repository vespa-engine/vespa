// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configbuilder.h"

namespace document {
namespace config_builder {
int32_t createFieldId(const vespalib::string &name, int32_t type) {
    StructDataType dummy("dummy", type);
    Field f(name, dummy, true);
    return f.getId();
}

int32_t DatatypeConfig::id_counter = 100;

DatatypeConfig::DatatypeConfig() {
    id = ++id_counter;
}

DatatypeConfig::DatatypeConfig(const DatatypeConfig&) = default;
DatatypeConfig& DatatypeConfig::operator=(const DatatypeConfig&) = default;

void DatatypeConfig::addNestedType(const TypeOrId &t) {
    if (t.has_type) {
        nested_types.insert(nested_types.end(),
                            t.type.nested_types.begin(),
                            t.type.nested_types.end());
        nested_types.push_back(t.type);
    }
}

}  // namespace config_builder
}  // namespace document
