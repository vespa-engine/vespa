// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Interface for inspector for an indexschema config.
 */
class IIndexschemaInspector {
public:
    virtual ~IIndexschemaInspector() { }
    virtual bool isStringIndex(const vespalib::string &name) const = 0;
};

} // namespace proton
