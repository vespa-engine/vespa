// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummydbowner.h"
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/server/maintenance_job_token_source.h>
#include <vespa/vespalib/util/shared_operation_throttler.h>

namespace proton {

DummyDBOwner::DummyDBOwner()
    : _registry(std::make_shared<DocumentDBReferenceRegistry>()),
      _sessionManager(std::make_unique<SessionManager>(10)),
      _lid_space_compaction_job_token_source(std::make_shared<MaintenanceJobTokenSource>()),
      _shared_replay_throttler(vespalib::SharedOperationThrottler::make_unlimited_throttler())
{}
DummyDBOwner::~DummyDBOwner() = default;

std::shared_ptr<IDocumentDBReferenceRegistry>
DummyDBOwner::getDocumentDBReferenceRegistry() const {
    return _registry;
}

matching::SessionManager &
DummyDBOwner::session_manager() {
    return *_sessionManager;
}

std::shared_ptr<MaintenanceJobTokenSource>
DummyDBOwner::get_lid_space_compaction_job_token_source()
{
    return _lid_space_compaction_job_token_source;
}

std::shared_ptr<vespalib::SharedOperationThrottler>
DummyDBOwner::shared_replay_throttler() const
{
    return _shared_replay_throttler;
}

}
