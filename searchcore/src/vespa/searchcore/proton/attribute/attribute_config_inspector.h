// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace vespa::config::search::internal { class InternalAttributesType; }
namespace search::attribute { class Config; }

namespace proton {

/*
 * Class used to find attribute config given attribute name based on config
 * from config server.
 */
class AttributeConfigInspector {
    using Config = search::attribute::Config;
    vespalib::hash_map<vespalib::string, std::unique_ptr<Config>> _hash;
public:
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    AttributeConfigInspector(const AttributesConfig& config);
    ~AttributeConfigInspector();
    const Config* get_config(const vespalib::string& name) const;
};

}
