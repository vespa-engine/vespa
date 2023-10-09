// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummydbowner.h"
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>

namespace proton {

DummyDBOwner::DummyDBOwner()
    : _registry(std::make_shared<DocumentDBReferenceRegistry>()),
      _sessionManager(std::make_unique<SessionManager>(10))
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

}
