// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "process.h"

#include <vespa/config/subscription/configsubscriber.hpp>
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

Process::~Process() = default;

void
Process::setupConfig(vespalib::duration subscribeTimeout)
{
    _document_cfg_handle      = _configSubscriber.subscribe<DocumentTypesConfig>(_configUri.getConfigId(), subscribeTimeout);
    _bucket_spaces_cfg_handle = _configSubscriber.subscribe<BucketspacesConfig>(_configUri.getConfigId(), subscribeTimeout);
    _comm_mgr_cfg_handle      = _configSubscriber.subscribe<CommunicationManagerConfig>(_configUri.getConfigId(), subscribeTimeout);
    _bouncer_cfg_handle       = _configSubscriber.subscribe<StorBouncerConfig>(_configUri.getConfigId(), subscribeTimeout);
    _distribution_cfg_handle  = _configSubscriber.subscribe<StorDistributionConfig>(_configUri.getConfigId(), subscribeTimeout);
    _server_cfg_handle        = _configSubscriber.subscribe<StorServerConfig>(_configUri.getConfigId(), subscribeTimeout);

    if (!_configSubscriber.nextConfig()) {
        throw vespalib::TimeoutException("Could not subscribe to configs within timeout");
    }
    _repos.push_back(DocumentTypeRepoFactory::make(*_document_cfg_handle->getConfig()));
    getContext().getComponentRegister().setDocumentTypeRepo(_repos.back());
}

bool
Process::configUpdated()
{
    _configSubscriber.nextGenerationNow();
    if (_document_cfg_handle->isChanged()) {
        LOG(info, "Document config detected changed");
        return true;
    }
    bool changed = (_bucket_spaces_cfg_handle->isChanged()
                 || _comm_mgr_cfg_handle->isChanged()
                 || _bouncer_cfg_handle->isChanged()
                 || _distribution_cfg_handle->isChanged()
                 || _server_cfg_handle->isChanged());
    return changed;
}

void
Process::updateConfig()
{
    if (_document_cfg_handle->isChanged()) {
        _repos.push_back(DocumentTypeRepoFactory::make(*_document_cfg_handle->getConfig()));
        getNode().setNewDocumentRepo(_repos.back());
    }
    if (_bucket_spaces_cfg_handle->isChanged()) {
        getNode().configure(_bucket_spaces_cfg_handle->getConfig());
    }
    if (_comm_mgr_cfg_handle->isChanged()) {
        getNode().configure(_comm_mgr_cfg_handle->getConfig());
    }
    if (_bouncer_cfg_handle->isChanged()) {
        getNode().configure(_bouncer_cfg_handle->getConfig());
    }
    if (_distribution_cfg_handle->isChanged()) {
        getNode().configure(_distribution_cfg_handle->getConfig());
    }
    if (_server_cfg_handle->isChanged()) {
        getNode().configure(_server_cfg_handle->getConfig());
    }
}

void
Process::shutdown()
{
    removeConfigSubscriptions(); // TODO remove? unused
}

int64_t
Process::getGeneration() const
{
    return _configSubscriber.getGeneration();
}

} // storage
