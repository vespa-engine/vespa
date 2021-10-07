// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchcommon/attribute/config.h>

namespace vespa::config::search::internal { class InternalAttributesType; }

namespace proton {

/*
 * Class used to find attribute config given attribute name based on config
 * from config server.
 */
class AttributeConfigInspector {
    vespalib::hash_map<vespalib::string, search::attribute::Config> _hash;
public:
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    AttributeConfigInspector(const AttributesConfig& config);
    ~AttributeConfigInspector();
    const search::attribute::Config* get_config(const vespalib::string& name) const;
};

}
