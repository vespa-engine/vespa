// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>

namespace proton
{

class IDocumentDBReferentRegistry;

class IDocumentDBOwner
{
public:
    virtual ~IDocumentDBOwner(void);

    virtual bool isInitializing() const = 0;

    virtual searchcorespi::IIndexManagerFactory::SP
    getIndexManagerFactory(const vespalib::stringref & name) const = 0;
    virtual uint32_t getDistributionKey() const = 0;
    virtual std::shared_ptr<IDocumentDBReferentRegistry> getDocumentDBReferentRegistry() const = 0;
};

} // namespace proton

