// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernode.h"
#include "bouncer.h"
#include "communicationmanager.h"
#include "changedbucketownershiphandler.h"
#include "mergethrottler.h"
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
#include <vespa/config/common/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".node.servicelayer");

namespace storage {

ServiceLayerNode::ServiceLayerBootstrapConfigs::ServiceLayerBootstrapConfigs() = default;
ServiceLayerNode::ServiceLayerBootstrapConfigs::~ServiceLayerBootstrapConfigs() = default;
ServiceLayerNode::ServiceLayerBootstrapConfigs::ServiceLayerBootstrapConfigs(ServiceLayerBootstrapConfigs&&) noexcept = default;
ServiceLayerNode::ServiceLayerBootstrapConfigs&
ServiceLayerNode::ServiceLayerBootstrapConfigs::operator=(ServiceLayerBootstrapConfigs&&) noexcept = default;

ServiceLayerNode::ServiceLayerNode(const config::ConfigUri& configUri,
                                   ServiceLayerNodeContext& context,
                                   const vespalib::HwInfo& hw_info,
                                   ServiceLayerBootstrapConfigs bootstrap_configs,
                                   ApplicationGenerationFetcher& generationFetcher,
                                   spi::PersistenceProvider& persistenceProvider,
                                   const VisitorFactory::Map& externalVisitors)
    : StorageNode(configUri, context, std::move(bootstrap_configs.storage_bootstrap_configs),
                  generationFetcher, std::make_unique<HostInfo>()),
      _context(context),
      _persistenceProvider(persistenceProvider),
      _externalVisitors(externalVisitors),
      _hw_info(hw_info),
      _persistence_bootstrap_config(std::move(bootstrap_configs.persistence_cfg)),
      _visitor_bootstrap_config(std::move(bootstrap_configs.visitor_cfg)),
      _filestor_bootstrap_config(std::move(bootstrap_configs.filestor_cfg)),
      _bouncer(nullptr),
      _bucket_manager(nullptr),
      _changed_bucket_ownership_handler(nullptr),
      _fileStorManager(nullptr),
      _merge_throttler(nullptr),
      _visitor_manager(nullptr),
      _modified_bucket_checker(nullptr),
      _init_has_been_called(false)
{
}

void
ServiceLayerNode::report(vespalib::JsonStream &stream) const
{
    using namespace vespalib::jsonstream;
    if (_bucket_manager) {
        stream << "metrics" << Object() << "values" << Array();
        _bucket_manager->report(stream);
        stream << End() << End();
    }
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
        initialize(*this);
    } catch (spi::HandledException& e) {
        requestShutdown("Failed to initialize: " + e.getMessage());
        throw;
    } catch (const config::ConfigTimeoutException &e) {
        LOG(warning, "Error subscribing to initial config: '%s'", e.what());
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
ServiceLayerNode::initializeNodeSpecific()
{
    // Give node state to mount point initialization, such that we can
    // get capacity set in reported node state.
    NodeStateUpdater::Lock::SP lock(_component->getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component->getStateUpdater().getReportedNodeState());

    ns.setCapacity(server_config().nodeCapacity);
    LOG(debug, "Adjusting reported node state to include capacity: %s", ns.toString().c_str());
    _component->getStateUpdater().setReportedNodeState(ns);
}

#define DIFFER(a) (!(oldC.a == newC.a))
#define ASSIGN(a) { oldC.a = newC.a; updated = true; }

void
ServiceLayerNode::handleLiveConfigUpdate(const InitialGuard & initGuard)
{
    if (_server_config.staging) {
        bool updated = false;
        vespa::config::content::core::StorServerConfigBuilder oldC(*_server_config.active);
        StorServerConfig& newC(*_server_config.staging);
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
                // FIXME this always gets overwritten by StorageNode::handleLiveConfigUpdate...! Intentional?
                _server_config.active = std::make_unique<vespa::config::content::core::StorServerConfig>(oldC);
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

    auto communication_manager = std::make_unique<CommunicationManager>(compReg, _configUri, communication_manager_config());
    _communicationManager = communication_manager.get();
    builder.add(std::move(communication_manager));
    auto bouncer = std::make_unique<Bouncer>(compReg, bouncer_config());
    _bouncer = bouncer.get();
    builder.add(std::move(bouncer));
    auto merge_throttler_up = std::make_unique<MergeThrottler>(server_config(), compReg, _hw_info);
    _merge_throttler = merge_throttler_up.get();
    builder.add(std::move(merge_throttler_up));
    auto bucket_ownership_handler = std::make_unique<ChangedBucketOwnershipHandler>(*_persistence_bootstrap_config, compReg);
    _changed_bucket_ownership_handler = bucket_ownership_handler.get();
    builder.add(std::move(bucket_ownership_handler));
    auto bucket_manager = std::make_unique<BucketManager>(server_config(), _context.getComponentRegister());
    _bucket_manager = bucket_manager.get();
    builder.add(std::move(bucket_manager));
    auto visitor_manager = std::make_unique<VisitorManager>(*_visitor_bootstrap_config, _context.getComponentRegister(),
                                                            static_cast<VisitorMessageSessionFactory &>(*this), _externalVisitors);
    _visitor_manager = visitor_manager.get();
    builder.add(std::move(visitor_manager));
    auto bucket_checker = std::make_unique<ModifiedBucketChecker>(_context.getComponentRegister(), _persistenceProvider, server_config());
    _modified_bucket_checker = bucket_checker.get();
    builder.add(std::move(bucket_checker));
    auto state_manager = releaseStateManager();
    auto filstor_manager = std::make_unique<FileStorManager>(*_filestor_bootstrap_config, _persistenceProvider,
                                                             _context.getComponentRegister(),
                                                             getDoneInitializeHandler(), state_manager->getHostInfo());
    _fileStorManager = filstor_manager.get();
    builder.add(std::move(filstor_manager));
    builder.add(std::move(state_manager));

    // Lifetimes of all referenced components shall outlive the last call going
    // through the SPI, as queues are flushed and worker threads joined when
    // the storage link chain is closed prior to destruction.
    auto error_listener = std::make_shared<ServiceLayerErrorListener>(*_component, *_merge_throttler);
    _fileStorManager->error_wrapper().register_error_listener(std::move(error_listener));

    // Purge config no longer needed
    _persistence_bootstrap_config.reset();
    _visitor_bootstrap_config.reset();
    _filestor_bootstrap_config.reset();
}

void
ServiceLayerNode::on_configure(const StorServerConfig& config)
{
    assert(_merge_throttler);
    _merge_throttler->on_configure(config);
    assert(_modified_bucket_checker);
    _modified_bucket_checker->on_configure(config);
}

void
ServiceLayerNode::on_configure(const PersistenceConfig& config)
{
    assert(_changed_bucket_ownership_handler);
    _changed_bucket_ownership_handler->on_configure(config);
}

void
ServiceLayerNode::on_configure(const StorVisitorConfig& config)
{
    assert(_visitor_manager);
    _visitor_manager->on_configure(config);
}

void
ServiceLayerNode::on_configure(const StorFilestorConfig& config)
{
    assert(_fileStorManager);
    _fileStorManager->on_configure(config);
}

ResumeGuard
ServiceLayerNode::pause()
{
    return _fileStorManager->getFileStorHandler().pause();
}

void ServiceLayerNode::perform_post_chain_creation_init_steps() {
    assert(_fileStorManager);
    assert(_bucket_manager);
    // After initialization, the node will immediately start communicating with the cluster
    // controller, exchanging host info. This host info contains a subset snapshot of the active
    // metrics, which includes the total bucket count, doc count etc. It is critical that
    // we must never report back host info _prior_ to having run at least one full sweep of
    // the bucket database, lest we risk transiently reporting zero buckets held by the
    // content node. Doing so could cause orchestration logic to perform operations based
    // on erroneous assumptions.
    // To avoid this, we explicitly force a full DB sweep and metric update prior to reporting
    // the node as up. Since this function is called prior to the CommunicationManager thread
    // being started, any CC health pings should also always happen after this init step.
    _fileStorManager->initialize_bucket_databases_from_provider();
    _bucket_manager->force_db_sweep_and_metric_update();
    _fileStorManager->complete_internal_initialization();
}

void ServiceLayerNode::on_bouncer_config_changed() {
    assert(_bouncer);
    _bouncer->on_configure(bouncer_config());
}

} // storage
