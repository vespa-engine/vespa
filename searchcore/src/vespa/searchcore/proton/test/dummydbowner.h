// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/idocumentdbowner.h>
#include <string>

namespace proton {

struct DummyDBOwner : IDocumentDBOwner {
    std::shared_ptr<IDocumentDBReferenceRegistry> _registry;
    std::unique_ptr<SessionManager> _sessionManager;
    std::shared_ptr<MaintenanceJobTokenSource> _lid_space_compaction_job_token_source;
    std::shared_ptr<vespalib::SharedOperationThrottler> _shared_replay_throttler;

    DummyDBOwner();
    ~DummyDBOwner() override;

    bool isInitializing() const override { return false; }

    uint32_t getDistributionKey() const override { return -1; }
    uint32_t getNumThreadsPerSearch() const override { return 1; }
    std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override;
    SessionManager & session_manager() override;
    std::shared_ptr<MaintenanceJobTokenSource> get_lid_space_compaction_job_token_source() override;
    std::shared_ptr<vespalib::SharedOperationThrottler> shared_replay_throttler() const override;
};

} // namespace proton
