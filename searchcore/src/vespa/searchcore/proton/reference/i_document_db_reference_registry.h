// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace proton {

class IDocumentDBReference;

/*
 * Interface class for a registry of named IDocumentDBReferences.
 */
class IDocumentDBReferenceRegistry
{
public:
    virtual ~IDocumentDBReferenceRegistry() = default;

    /*
     * Get named IDocumentDBReference.  Block while it doesn't exist.
     */
    virtual std::shared_ptr<IDocumentDBReference> get(vespalib::stringref name) const = 0;

    /*
     * Get named IDocumentDBReference.  Returns empty shared pointer if
     * it doesn't exist.
     */
    virtual std::shared_ptr<IDocumentDBReference> tryGet(vespalib::stringref name) const = 0;

    virtual void add(vespalib::stringref name, std::shared_ptr<IDocumentDBReference> referee) = 0;

    virtual void remove(vespalib::stringref name) = 0;
};

} // namespace proton
