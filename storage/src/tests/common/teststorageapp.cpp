// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "teststorageapp.h"
#include <vespa/storage/common/content_bucket_db_options.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/config-fleetcontroller.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configgetter.hpp>
#include <thread>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".test.servicelayerapp");

using storage::framework::defaultimplementation::ComponentRegisterImpl;

namespace storage {

TestStorageApp::TestStorageApp(StorageComponentRegisterImpl::UP compReg,
                               const lib::NodeType& type, NodeIndex index,
                               vespalib::stringref configId)
    : TestComponentRegister(ComponentRegisterImpl::UP(std::move(compReg))),
      _compReg(dynamic_cast<StorageComponentRegisterImpl&>(TestComponentRegister::getComponentRegister())),
      _docMan(),
      _nodeStateUpdater(type),
      _configId(configId),
      _node_identity("test_cluster", type, index),
      _initialized(false)
{
        // Use config to adjust values
    vespalib::string clusterName = "mycluster";
    uint32_t redundancy = 2;
    uint32_t nodeCount = 10;
    if (!configId.empty()) {
        config::ConfigUri uri(configId);
        std::unique_ptr<vespa::config::content::core::StorServerConfig> serverConfig = config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(uri.getConfigId(), uri.getContext());
        clusterName = serverConfig->clusterName;
        if (index == 0xffff) index = serverConfig->nodeIndex;
        redundancy = config::ConfigGetter<vespa::config::content::StorDistributionConfig>::getConfig(uri.getConfigId(), uri.getContext())->redundancy;
        nodeCount = config::ConfigGetter<vespa::config::content::FleetcontrollerConfig>::getConfig(uri.getConfigId(), uri.getContext())->totalStorageCount;
    } else {
        if (index == 0xffff) index = 0;
    }
    if (index >= nodeCount) nodeCount = index + 1;
    if (redundancy > nodeCount) redundancy = nodeCount;

    _compReg.setNodeInfo(clusterName, type, index);
    _compReg.setNodeStateUpdater(_nodeStateUpdater);
    _compReg.setDocumentTypeRepo(_docMan.getTypeRepoSP());
    _compReg.setBucketIdFactory(document::BucketIdFactory());
    auto distr = std::make_shared<lib::Distribution>(
            lib::Distribution::getDefaultDistributionConfig(redundancy, nodeCount));
    _compReg.setDistribution(distr);
}

TestStorageApp::~TestStorageApp() = default;

void
TestStorageApp::setDistribution(Redundancy redundancy, NodeCount nodeCount)
{
    auto distr = std::make_shared<lib::Distribution>(
            lib::Distribution::getDefaultDistributionConfig(redundancy, nodeCount));
    _compReg.setDistribution(distr);
}

void
TestStorageApp::setTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> repo)
{
    _compReg.setDocumentTypeRepo(std::move(repo));
}

void
TestStorageApp::setClusterState(const lib::ClusterState& c)
{
    _nodeStateUpdater.setClusterState(std::make_shared<lib::ClusterState>(c));
}

namespace {
NodeIndex getIndexFromConfig(vespalib::stringref configId) {
    if (!configId.empty()) {
        config::ConfigUri uri(configId);
        return NodeIndex(
            config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(uri.getConfigId(), uri.getContext())->nodeIndex);
    }
    return NodeIndex(0);
}

VESPA_THREAD_STACK_TAG(test_executor)
}

TestServiceLayerApp::TestServiceLayerApp(vespalib::stringref configId)
    : TestStorageApp(std::make_unique<ServiceLayerComponentRegisterImpl>(ContentBucketDbOptions()),
                     lib::NodeType::STORAGE, getIndexFromConfig(configId), configId),
      _compReg(dynamic_cast<ServiceLayerComponentRegisterImpl&>(TestStorageApp::getComponentRegister())),
      _persistenceProvider(),
      _executor(vespalib::SequencedTaskExecutor::create(test_executor, 1)),
      _host_info()
{
    lib::NodeState ns(*_nodeStateUpdater.getReportedNodeState());
    _nodeStateUpdater.setReportedNodeState(ns);
}

TestServiceLayerApp::TestServiceLayerApp(NodeIndex index,
                                         vespalib::stringref configId)
    : TestStorageApp(std::make_unique<ServiceLayerComponentRegisterImpl>(ContentBucketDbOptions()),
                     lib::NodeType::STORAGE, index, configId),
      _compReg(dynamic_cast<ServiceLayerComponentRegisterImpl&>(TestStorageApp::getComponentRegister())),
      _persistenceProvider(),
      _executor(vespalib::SequencedTaskExecutor::create(test_executor, 1)),
      _host_info()
{
    lib::NodeState ns(*_nodeStateUpdater.getReportedNodeState());
    _nodeStateUpdater.setReportedNodeState(ns);
}

TestServiceLayerApp::~TestServiceLayerApp() = default;

void
TestServiceLayerApp::setupDummyPersistence()
{
    auto provider = std::make_unique<spi::dummy::DummyPersistence>(getTypeRepo());
    provider->initialize();
    setPersistenceProvider(std::move(provider));
}

void
TestServiceLayerApp::setPersistenceProvider(PersistenceProviderUP provider)
{
    _persistenceProvider = std::move(provider);
}

spi::PersistenceProvider&
TestServiceLayerApp::getPersistenceProvider()
{
    if ( ! _persistenceProvider) {
        throw vespalib::IllegalStateException("Persistence provider requested but not initialized.", VESPA_STRLOC);
    }
    return *_persistenceProvider;
}

namespace {
    template<typename T>
    T getConfig(vespalib::stringref configId) {
        config::ConfigUri uri(configId);
        return *config::ConfigGetter<T>::getConfig(uri.getConfigId(), uri.getContext());
    }
}

void
TestDistributorApp::configure(vespalib::stringref id)
{
    if (id.empty()) return;
    auto dc(getConfig<vespa::config::content::core::StorDistributormanagerConfig>(id));
    _compReg.setDistributorConfig(dc);
    auto vc(getConfig<vespa::config::content::core::StorVisitordispatcherConfig>(id));
    _compReg.setVisitorConfig(vc);
}

TestDistributorApp::TestDistributorApp(vespalib::stringref configId)
    : TestStorageApp(
            std::make_unique<DistributorComponentRegisterImpl>(),
            lib::NodeType::DISTRIBUTOR, getIndexFromConfig(configId), configId),
      _compReg(dynamic_cast<DistributorComponentRegisterImpl&>(TestStorageApp::getComponentRegister())),
      _lastUniqueTimestampRequested(0),
      _uniqueTimestampCounter(0)
{
    _compReg.setTimeCalculator(*this);
    configure(configId);
}

TestDistributorApp::TestDistributorApp(NodeIndex index, vespalib::stringref configId)
    : TestStorageApp(
            std::make_unique<DistributorComponentRegisterImpl>(),
            lib::NodeType::DISTRIBUTOR, index, configId),
      _compReg(dynamic_cast<DistributorComponentRegisterImpl&>(TestStorageApp::getComponentRegister())),
      _lastUniqueTimestampRequested(0),
      _uniqueTimestampCounter(0)
{
    _compReg.setTimeCalculator(*this);
    configure(configId);
}

TestDistributorApp::~TestDistributorApp() = default;

api::Timestamp
TestDistributorApp::generate_unique_timestamp()
{
    std::lock_guard guard(_accessLock);
    uint64_t timeNow(vespalib::count_s(getClock().getSystemTime().time_since_epoch()));
    if (timeNow == _lastUniqueTimestampRequested) {
        ++_uniqueTimestampCounter;
    } else {
        if (timeNow < _lastUniqueTimestampRequested) {
            LOG(error, "Time has moved backwards, from %" PRIu64 " to %" PRIu64 ".",
                    _lastUniqueTimestampRequested, timeNow);
        }
        _lastUniqueTimestampRequested = timeNow;
        _uniqueTimestampCounter = 0;
    }

    return _lastUniqueTimestampRequested * 1000000ll + _uniqueTimestampCounter;
}

} // storage
