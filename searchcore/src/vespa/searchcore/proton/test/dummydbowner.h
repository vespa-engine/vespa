// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/idocumentdbowner.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

struct DummyDBOwner : IDocumentDBOwner {
    std::shared_ptr<IDocumentDBReferenceRegistry> _registry;
    std::unique_ptr<SessionManager> _sessionManager;

    DummyDBOwner();
    ~DummyDBOwner() override;

    bool isInitializing() const override { return false; }

    uint32_t getDistributionKey() const override { return -1; }
    uint32_t getNumThreadsPerSearch() const override { return 1; }
    std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override;
    SessionManager & session_manager() override;
};

} // namespace proton
