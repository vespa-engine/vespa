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
    virtual std::shared_ptr<IDocumentDBReferent> getDocumentDBReferent(vespalib::stringref name) const = 0;

    virtual void addDocumentDBReferent(vespalib::stringref name, std::shared_ptr<IDocumentDBReferent> referee) = 0;

    virtual void removeDocumentDBReferent(vespalib::stringref name) = 0;
};

} // namespace proton
