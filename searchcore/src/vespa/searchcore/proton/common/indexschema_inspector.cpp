// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexschema_inspector.h"
#include <vespa/config-indexschema.h>
#include "config_hash.hpp"

namespace proton {

IndexschemaInspector::IndexschemaInspector(const IndexschemaConfig &config)
    : IIndexschemaInspector(),
      _hash(config.indexfield)
{
}

IndexschemaInspector::~IndexschemaInspector()
{
}

bool
IndexschemaInspector::isStringIndex(const vespalib::string &name) const
{
    auto index = _hash.lookup(name);
    if (index != nullptr) {
        if (index->datatype == IndexschemaConfig::Indexfield::Datatype::STRING) {
            return true;
        }
    }
    return false;
}

} // namespace proton
