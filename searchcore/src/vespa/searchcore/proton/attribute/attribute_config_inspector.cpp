// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_config_inspector.h"
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/config-attributes.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::attribute::ConfigConverter;

namespace proton {

AttributeConfigInspector::AttributeConfigInspector(const AttributesConfig& config)
    : _hash()
{
    for (auto& attr : config.attribute) {
        auto res = _hash.insert(std::make_pair(attr.name, ConfigConverter::convert(attr)));
        assert(res.second);
    }
}

AttributeConfigInspector::~AttributeConfigInspector() = default;

const search::attribute::Config*
AttributeConfigInspector::get_config(const vespalib::string& name) const
{
    auto itr = _hash.find(name);
    return (itr != _hash.end()) ? &itr->second : nullptr;
}

}
