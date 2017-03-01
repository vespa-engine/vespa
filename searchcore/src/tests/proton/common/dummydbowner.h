// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/idocumentdbowner.h>
#include <vespa/searchcore/proton/reference/document_db_referent_registry.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton
{

struct DummyDBOwner : IDocumentDBOwner {
    std::shared_ptr<IDocumentDBReferentRegistry> _registry;

    DummyDBOwner()
        : _registry(std::make_shared<DocumentDBReferentRegistry>())
    {
    }

    bool isInitializing() const override { return false; }

    searchcorespi::IIndexManagerFactory::SP
    getIndexManagerFactory(const vespalib::stringref & ) const override {
        return searchcorespi::IIndexManagerFactory::SP();
    }
    uint32_t getDistributionKey() const override { return -1; }
    std::shared_ptr<IDocumentDBReferentRegistry> getDocumentDBReferentRegistry() const override {
        return _registry;
    }
};

} // namespace proton

