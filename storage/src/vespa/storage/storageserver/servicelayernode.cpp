// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernode.h"
#include "bouncer.h"
#include "communicationmanager.h"
#include "changedbucketownershiphandler.h"
#include "mergethrottler.h"
#include "opslogger.h"
#include "statemanager.h"
#include "priorityconverter.h"
#include "service_layer_error_listener.h"
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/visiting/messagebusvisitormessagesession.h>
#include <vespa/storage/visiting/visitormanager.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/persistence/spi/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/messagebus/rpcmessagebus.h>

#include <vespa/log/log.h>
LOG_SETUP(".node.servicelayer");

namespace storage {

ServiceLayerNode::ServiceLayerNode(const config::ConfigUri & configUri, ServiceLayerNodeContext& context,
                                   ApplicationGenerationFetcher& generationFetcher,
                                   spi::PersistenceProvider& persistenceProvider,
                                   const VisitorFactory::Map& externalVisitors)
    : StorageNode(configUri, context, generationFetcher, std::make_unique<HostInfo>()),
      _context(context),
      _persistenceProvider(persistenceProvider),
      _externalVisitors(externalVisitors),
      _fileStorManager(nullptr),
      _init_has_been_called(false)
{
}

void ServiceLayerNode::init()
{
    assert( ! _init_has_been_called);
    _init_has_been_called = true;
    spi::Result initResult(_persistenceProvider.initialize());
    if (initResult.hasError()) {
        LOG(error, "Failed to initialize persistence provider: %s", initResult.toString().c_str());
        throw spi::HandledException("Failed provider init: " + initResult.toString(), VESPA_STRLOC);
    }

    try{
        initialize();
    } catch (spi::HandledException& e) {
        requestShutdown("Failed to initialize: " + e.getMessage());
        throw;
    } catch (const vespalib::NetworkSetupFailureException & e) {
        LOG(warning, "Network failure: '%s'", e.what());
        throw;
    } catch (const vespalib::Exception & e) {
        LOG(error, "Caught exception %s during startup. Calling destruct functions in hopes of dying gracefully.",
            e.getMessage().c_str());
        requestShutdown("Failed to initialize: " + e.getMessage());
        throw;
    }
}

ServiceLayerNode::~ServiceLayerNode()
{
    assert(_init_has_been_called);
    shutdown();
}

void
ServiceLayerNode::subscribeToConfigs()
{
    StorageNode::subscribeToConfigs();
    _configFetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
}

void
ServiceLayerNode::removeConfigSubscriptions()
{
    StorageNode::removeConfigSubscriptions();
    _configFetcher.reset();
}

void
ServiceLayerNode::initializeNodeSpecific()
{
    // Give node state to mount point initialization, such that we can
    // get capacity set in reported node state.
    NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());

    ns.setCapacity(_serverConfig->nodeCapacity);
    LOG(debug, "Adjusting reported node state to include capacity: %s", ns.toString().c_str());
    _component->getStateUpdater().setReportedNodeState(ns);
}

#define DIFFER(a) (!(oldC.a == newC.a))
#define ASSIGN(a) { oldC.a = newC.a; updated = true; }

void
ServiceLayerNode::handleLiveConfigUpdate(const InitialGuard & initGuard)
{
    if (_newServerConfig) {
        bool updated = false;
        vespa::config::content::core::StorServerConfigBuilder oldC(*_serverConfig);
        StorServerConfig& newC(*_newServerConfig);
        {
            updated = false;
            NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
            lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());
            if (DIFFER(nodeCapacity)) {
                LOG(info, "Live config update: Updating node capacity from %f to %f.",
                    oldC.nodeCapacity, newC.nodeCapacity);
                ASSIGN(nodeCapacity);
                ns.setCapacity(newC.nodeCapacity);
            }
            if (updated) {
                _serverConfig.reset(new vespa::config::content::core::StorServerConfig(oldC));
                _component->getStateUpdater().setReportedNodeState(ns);
            }
        }
    }
    StorageNode::handleLiveConfigUpdate(initGuard);
}

VisitorMessageSession::UP
ServiceLayerNode::createSession(Visitor& visitor, VisitorThread& thread)
{
    auto mbusSession = std::make_unique<MessageBusVisitorMessageSession>(visitor, thread);
    mbus::SourceSessionParams srcParams;
    srcParams.setThrottlePolicy(mbus::IThrottlePolicy::SP());
    srcParams.setReplyHandler(*mbusSession);
    mbusSession->setSourceSession(_communicationManager->getMessageBus().getMessageBus().createSourceSession(srcParams));
    return VisitorMessageSession::UP(std::move(mbusSession));
}

documentapi::Priority::Value
ServiceLayerNode::toDocumentPriority(uint8_t storagePriority) const
{
    return _communicationManager->getPriorityConverter().toDocumentPriority(storagePriority);
}

void
ServiceLayerNode::createChain(IStorageChainBuilder &builder)
{
    ServiceLayerComponentRegister& compReg(_context.getComponentRegister());

    auto communication_manager = std::make_unique<CommunicationManager>(compReg, _configUri);
    _communicationManager = communication_manager.get();
    builder.add(std::move(communication_manager));
    builder.add(std::make_unique<Bouncer>(compReg, _configUri));
    builder.add(std::make_unique<OpsLogger>(compReg, _configUri));
    auto merge_throttler_up = std::make_unique<MergeThrottler>(_configUri, compReg);
    auto merge_throttler = merge_throttler_up.get();
    builder.add(std::move(merge_throttler_up));
    builder.add(std::make_unique<ChangedBucketOwnershipHandler>(_configUri, compReg));
    builder.add(std::make_unique<BucketManager>(_configUri, _context.getComponentRegister()));
    builder.add(std::make_unique<VisitorManager>(_configUri, _context.getComponentRegister(), static_cast<VisitorMessageSessionFactory &>(*this), _externalVisitors));
    builder.add(std::make_unique<ModifiedBucketChecker>(
            _context.getComponentRegister(), _persistenceProvider, _configUri));
    auto state_manager = releaseStateManager();
    auto filstor_manager = std::make_unique<FileStorManager>(_configUri, _persistenceProvider, _context.getComponentRegister(),
                                                             getDoneInitializeHandler(), state_manager->getHostInfo());
    _fileStorManager = filstor_manager.get();
    builder.add(std::move(filstor_manager));
    builder.add(std::move(state_manager));

    // Lifetimes of all referenced components shall outlive the last call going
    // through the SPI, as queues are flushed and worker threads joined when
    // the storage link chain is closed prior to destruction.
    auto error_listener = std::make_shared<ServiceLayerErrorListener>(*_component, *merge_throttler);
    _fileStorManager->error_wrapper().register_error_listener(std::move(error_listener));
}

ResumeGuard
ServiceLayerNode::pause()
{
    return _fileStorManager->getFileStorHandler().pause();
}

void ServiceLayerNode::perform_post_chain_creation_init_steps() {
    assert(_fileStorManager);
    _fileStorManager->initialize_bucket_databases_from_provider();
}

} // storage
