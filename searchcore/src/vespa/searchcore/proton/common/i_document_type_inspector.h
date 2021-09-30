// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace proton {

/**
 * Interface used to inspect which fields are present in a document type.
 */
struct IDocumentTypeInspector
{
    typedef std::shared_ptr<IDocumentTypeInspector> SP;

    virtual ~IDocumentTypeInspector() =default;

    virtual bool hasUnchangedField(const vespalib::string &name) const = 0;
};

} // namespace proton

