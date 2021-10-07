// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/idocumentdbowner.h>
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

struct DummyDBOwner : IDocumentDBOwner {
    std::shared_ptr<IDocumentDBReferenceRegistry> _registry;

    DummyDBOwner()
        : _registry(std::make_shared<DocumentDBReferenceRegistry>())
    {}
    ~DummyDBOwner() {}

    bool isInitializing() const override { return false; }

    uint32_t getDistributionKey() const override { return -1; }
    std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override {
        return _registry;
    }
};

} // namespace proton
