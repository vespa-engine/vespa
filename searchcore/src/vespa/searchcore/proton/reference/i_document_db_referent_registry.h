// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace proton {

class IDocumentDBReferent;

/*
 * Interface class for a registry of named IDocumentDBReferents.
 */
class IDocumentDBReferentRegistry
{
public:
    virtual ~IDocumentDBReferentRegistry() { }

    /*
     * Get named IDocumentDBReferent.  Block while it doesn't exist.
     */
    virtual std::shared_ptr<IDocumentDBReferent> get(vespalib::stringref name) const = 0;

    /*
     * Get named IDocumentDBReferent.  Returns empty shared pointer if
     * it doesn't exist.
     */
    virtual std::shared_ptr<IDocumentDBReferent> tryGet(vespalib::stringref name) const = 0;

    virtual void add(vespalib::stringref name, std::shared_ptr<IDocumentDBReferent> referee) = 0;

    virtual void remove(vespalib::stringref name) = 0;
};

} // namespace proton
