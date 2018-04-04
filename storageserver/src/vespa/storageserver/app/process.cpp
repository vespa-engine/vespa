// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process.h"
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/storage/storageserver/storagenode.h>
#include <vespa/storage/storageserver/storagenodecontext.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".process");

using document::DocumentTypeRepoFactory;

namespace storage {

Process::Process(const config::ConfigUri & configUri)
    : _configUri(configUri),
      _configSubscriber(_configUri.getContext())
{ }

void
Process::setupConfig(uint64_t subscribeTimeout)
{
    _documentHandler = _configSubscriber.subscribe<document::DocumenttypesConfig>(_configUri.getConfigId(), subscribeTimeout);
    if (!_configSubscriber.nextConfig()) {
        throw vespalib::TimeoutException("Could not subscribe to document config within timeout");
    }
    _repos.push_back(DocumentTypeRepoFactory::make(*_documentHandler->getConfig()));
    getContext().getComponentRegister().setDocumentTypeRepo(_repos.back());
}

bool
Process::configUpdated()
{
    _configSubscriber.nextGeneration(0);
    if (_documentHandler->isChanged()) {
        LOG(info, "Document config detected changed");
        return true;
    }
    return false;
}

void
Process::updateConfig()
{
    if (_documentHandler->isChanged()) {
        _repos.push_back(DocumentTypeRepoFactory::make(*_documentHandler->getConfig()));
        getNode().setNewDocumentRepo(_repos.back());
    }
}

void
Process::shutdown()
{
    removeConfigSubscriptions();
}

int64_t
Process::getGeneration() const
{
    return _configSubscriber.getGeneration();
}

} // storage
