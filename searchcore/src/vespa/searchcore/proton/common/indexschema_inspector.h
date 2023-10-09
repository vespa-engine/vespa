// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_indexschema_inspector.h"
#include "config_hash.h"
#include <vespa/config-indexschema.h>

namespace proton {

/**
 * Inspector for an indexschema config.
 */
class IndexschemaInspector : public IIndexschemaInspector {
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;
    ConfigHash<IndexschemaConfig::Indexfield> _hash;
public:
    IndexschemaInspector(const IndexschemaConfig &config);
    ~IndexschemaInspector();
    bool isStringIndex(const vespalib::string &name) const override;
};

} // namespace proton
