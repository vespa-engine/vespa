// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestormanager.h"
#include "filestorhandlerimpl.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/storage/persistence/persistencehandler.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/provider_error_wrapper.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/string_escape.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.filestor.manager");

using document::BucketSpace;
using std::shared_ptr;
using vespa::config::content::StorFilestorConfig;
using vespalib::CpuUsage;
using vespalib::make_string_short::fmt;

namespace {

VESPA_THREAD_STACK_TAG(response_executor)

}

namespace storage {
namespace {

class BucketExecutorWrapper : public spi::BucketExecutor {
public:
    BucketExecutorWrapper(spi::BucketExecutor & executor) noexcept : _executor(executor) { }

    void execute(const spi::Bucket &bucket, std::unique_ptr<spi::BucketTask> task) override {
        _executor.execute(bucket, std::move(task));
    }

private:
    spi::BucketExecutor & _executor;
};

}

FileStorManager::
FileStorManager(const config::ConfigUri & configUri, spi::PersistenceProvider& provider,
                ServiceLayerComponentRegister& compReg, DoneInitializeHandler& init_handler,
                HostInfo& hostInfoReporterRegistrar)
    : StorageLinkQueued("File store manager", compReg),
      framework::HtmlStatusReporter("filestorman", "File store manager"),
      _compReg(compReg),
      _component(compReg, "filestormanager"),
      _provider(std::make_unique<ProviderErrorWrapper>(provider)),
      _init_handler(init_handler),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _persistenceHandlers(),
      _threads(),
      _bucketOwnershipNotifier(std::make_unique<BucketOwnershipNotifier>(_component, *this)),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _use_async_message_handling_on_schedule(false),
      _metrics(std::make_unique<FileStorMetrics>()),
      _mem_trap_1(std::make_unique<vespalib::HeapMemoryTrap>(1)),
      _mem_trap_2(std::make_unique<vespalib::HeapMemoryTrap>(2)),
      _mem_trap_3(std::make_unique<vespalib::HeapMemoryTrap>(3)),
      _mem_trap_4(std::make_unique<vespalib::HeapMemoryTrap>(16)),
      _filestorHandler(),
      _sequencedExecutor(),
      _closed(false),
      _lock(),
      _host_info_reporter(_component.getStateUpdater()),
      _resource_usage_listener_registration(provider.register_resource_usage_listener(_host_info_reporter))
{
    _configFetcher->subscribe(configUri.getConfigId(), this);
    _configFetcher->start();
    _component.registerMetric(*_metrics);
    _component.registerStatusPage(*this);
    _component.getStateUpdater().addStateListener(*this);
    hostInfoReporterRegistrar.registerReporter(&_host_info_reporter);
    propagateClusterStates();
}

FileStorManager::~FileStorManager()
{
    closeNextLink();
    LOG(debug, "Deleting link %s. Giving filestor threads stop signal.",
        toString().c_str());

    for (const auto & thread : _threads) {
        if (thread) {
            thread->getThread().interrupt();
        }
    }
    LOG(debug, "Closing all filestor queues, answering queued messages. New messages will be refused.");
    _filestorHandler->close();
    for (const auto & thread : _threads) {
        if (thread) {
            thread->getThread().join();
        }
    }

    LOG(debug, "Deleting filestor threads. Waiting for their current operation "
               "to finish. Stop their threads and delete objects.");
    _threads.clear();
}

void
FileStorManager::print(std::ostream& out, bool , const std::string& ) const
{
    out << "FileStorManager";
}

ProviderErrorWrapper &
FileStorManager::error_wrapper() noexcept {
    return static_cast<ProviderErrorWrapper &>(*_provider);
}

namespace {

uint32_t computeNumResponseThreads(int configured) {
    return (configured <= 0) ? std::max(1u, std::thread::hardware_concurrency()/4) : configured;
}

vespalib::Executor::OptimizeFor
selectSequencer(StorFilestorConfig::ResponseSequencerType sequencerType) {
    switch (sequencerType) {
        case StorFilestorConfig::ResponseSequencerType::THROUGHPUT:
            return vespalib::Executor::OptimizeFor::THROUGHPUT;
        case StorFilestorConfig::ResponseSequencerType::LATENCY:
            return vespalib::Executor::OptimizeFor::LATENCY;
        case StorFilestorConfig::ResponseSequencerType::ADAPTIVE:
        default:
            return vespalib::Executor::OptimizeFor::ADAPTIVE;
    }
}

vespalib::SharedOperationThrottler::DynamicThrottleParams
dynamic_throttle_params_from_config(const StorFilestorConfig& config, uint32_t num_threads)
{
    const auto& cfg_params = config.asyncOperationThrottler;
    auto win_size_incr = std::max(static_cast<uint32_t>(std::max(cfg_params.windowSizeIncrement, 1)), num_threads);

    vespalib::SharedOperationThrottler::DynamicThrottleParams params;
    params.window_size_increment        = win_size_incr;
    params.min_window_size              = std::max(win_size_incr, static_cast<uint32_t>(std::max(1, cfg_params.minWindowSize)));
    params.max_window_size              = (cfg_params.maxWindowSize > 0)
                                           ? std::max(static_cast<uint32_t>(cfg_params.maxWindowSize), params.min_window_size)
                                           : INT_MAX;
    params.resize_rate                  = cfg_params.resizeRate;
    params.window_size_decrement_factor = cfg_params.windowSizeDecrementFactor;
    params.window_size_backoff          = cfg_params.windowSizeBackoff;
    return params;
}

#ifdef __PIC__
#define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("initial-exec")))
#else
#define TLS_LINKAGE __attribute__((visibility("hidden"), tls_model("local-exec")))
#endif

thread_local PersistenceHandler * _G_threadLocalHandler TLS_LINKAGE = nullptr;

size_t
computeAllPossibleHandlerThreads(const vespa::config::content::StorFilestorConfig & cfg) {
    return cfg.numThreads +
           computeNumResponseThreads(cfg.numResponseThreads) +
           cfg.numNetworkThreads +
           cfg.numVisitorThreads;
}

}

PersistenceHandler &
FileStorManager::createRegisteredHandler(const ServiceLayerComponent & component)
{
    std::lock_guard guard(_lock);
    size_t index = _persistenceHandlers.size();
    assert(index < _metrics->threads.size());
    _persistenceHandlers.push_back(
            std::make_unique<PersistenceHandler>(*_sequencedExecutor, component,
                                                 *_config, *_provider, *_filestorHandler,
                                                 *_bucketOwnershipNotifier, *_metrics->threads[index]));
    return *_persistenceHandlers.back();
}

PersistenceHandler &
FileStorManager::getThreadLocalHandler() {
    if (_G_threadLocalHandler == nullptr) {
        _G_threadLocalHandler = & createRegisteredHandler(_component);
    }
    return *_G_threadLocalHandler;
}

void
FileStorManager::configure(std::unique_ptr<StorFilestorConfig> config)
{
    // If true, this is not the first configure.
    const bool liveUpdate = ! _threads.empty();

    _use_async_message_handling_on_schedule = config->useAsyncMessageHandlingOnSchedule;
    _host_info_reporter.set_noise_level(config->resourceUsageReporterNoiseLevel);
    const bool use_dynamic_throttling = ((config->asyncOperationThrottlerType  == StorFilestorConfig::AsyncOperationThrottlerType::DYNAMIC) ||
                                         (config->asyncOperationThrottler.type == StorFilestorConfig::AsyncOperationThrottler::Type::DYNAMIC));
    const bool throttle_merge_feed_ops = config->asyncOperationThrottler.throttleIndividualMergeFeedOps;

    if (!liveUpdate) {
        _config = std::move(config);
        uint32_t numThreads = std::max(1, _config->numThreads);
        uint32_t numStripes = std::max(1u, numThreads / 2);
        _metrics->initDiskMetrics(numStripes, computeAllPossibleHandlerThreads(*_config));
        auto dyn_params = dynamic_throttle_params_from_config(*_config, numThreads);

        _filestorHandler = std::make_unique<FileStorHandlerImpl>(numThreads, numStripes, *this, *_metrics,
                                                                 _compReg, dyn_params);
        uint32_t numResponseThreads = computeNumResponseThreads(_config->numResponseThreads);
        _sequencedExecutor = vespalib::SequencedTaskExecutor::create(CpuUsage::wrap(response_executor, CpuUsage::Category::WRITE),
                                                                     numResponseThreads, 10000,
                                                                     true, selectSequencer(_config->responseSequencerType));
        assert(_sequencedExecutor);
        LOG(spam, "Setting up the disk");
        for (uint32_t i = 0; i < numThreads; i++) {
            _threads.push_back(std::make_unique<PersistenceThread>(createRegisteredHandler(_component),
                                                                   *_filestorHandler, i % numStripes, _component));
        }
        _bucketExecutorRegistration = _provider->register_executor(std::make_shared<BucketExecutorWrapper>(*this));
    } else {
        assert(_filestorHandler);
        auto updated_dyn_throttle_params = dynamic_throttle_params_from_config(*config, _threads.size());
        _filestorHandler->reconfigure_dynamic_throttler(updated_dyn_throttle_params);
    }
    // TODO remove once desired dynamic throttling behavior is set in stone
    {
        _filestorHandler->use_dynamic_operation_throttling(use_dynamic_throttling);
        _filestorHandler->set_throttle_apply_bucket_diff_ops(!throttle_merge_feed_ops);
        std::lock_guard guard(_lock);
        for (auto& ph : _persistenceHandlers) {
            ph->set_throttle_merge_feed_ops(throttle_merge_feed_ops);
        }
    }
}

void
FileStorManager::replyDroppedOperation(api::StorageMessage& msg, const document::Bucket& bucket,
                                       api::ReturnCode::Result returnCode, vespalib::stringref reason)
{
    std::ostringstream error;
    error << "Dropping " << msg.getType() << " to bucket "
          << bucket.toString() << ". Reason: " << reason;
    LOGBT(debug, bucket.toString(), "%s", error.str().c_str());
    if (!msg.getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply = static_cast<api::StorageCommand&>(msg).makeReply();
        reply->setResult(api::ReturnCode(returnCode, error.str()));
        sendUp(reply);
    }
}

void
FileStorManager::replyWithBucketNotFound(api::StorageMessage& msg, const document::Bucket& bucket)
{
    replyDroppedOperation(msg, bucket, api::ReturnCode::BUCKET_NOT_FOUND, "bucket does not exist");
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToDisk(api::StorageMessage& msg, const document::Bucket& bucket)
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
            bucket.getBucketId(), "FileStorManager::mapOperationToDisk"));
    if (!entry.exist()) {
        replyWithBucketNotFound(msg, bucket);
    }
    return entry;
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToBucketAndDisk(api::BucketCommand& cmd, const document::DocumentId* docId)
{
    StorBucketDatabase &database = _component.getBucketDatabase(cmd.getBucket().getBucketSpace());
    StorBucketDatabase::WrappedEntry entry(database.get(
            cmd.getBucketId(), "FileStorManager::mapOperationToBucketAndDisk"));
    if (!entry.exist()) {
        document::BucketId specific(cmd.getBucketId());
        if (docId) {
            specific = _bucketIdFactory.getBucketId(*docId);
        }
        using BucketMap = std::map<document::BucketId, StorBucketDatabase::WrappedEntry>;
        std::shared_ptr<api::StorageReply> reply;
        {
            BucketMap results( database.getContained( specific, "FileStorManager::mapOperationToBucketAndDisk-2"));
            if (results.size() == 1) {
                LOG(debug, "Remapping %s operation to specific %s versus non-existing %s to %s.",
                    cmd.toString().c_str(), specific.toString().c_str(),
                    cmd.getBucketId().toString().c_str(),
                    results.begin()->first.toString().c_str());
                cmd.remapBucketId(results.begin()->first);
                return std::move(results.begin()->second);
            }
            std::ostringstream error;
            error << "Dropping " << cmd.getType() << " to bucket "
                  << cmd.getBucketId().toString() << " since bucket doesnt exist. ";
            if (results.size() > 1) {
                error << "Bucket was inconsistent with " << results.size()
                      << " entries so no automatic remapping done:";
                BucketMap::const_iterator it = results.begin();
                for (uint32_t i=0; i <= 4 && it != results.end(); ++it, ++i) {
                    error << " " << it->first;
                }
                if (it != results.end()) {
                    error << " ...";
                }
            } else {
                error << "No other bucket exists that can contain this data either.";
            }
            LOGBT(debug, cmd.getBucketId().toString(), "%s", error.str().c_str());

            reply = static_cast<api::StorageCommand&>(cmd).makeReply();
            reply->setResult( api::ReturnCode( api::ReturnCode::BUCKET_NOT_FOUND, error.str()));
        }
        sendUp(reply);
    }
    return entry;
}

bool
FileStorManager::handlePersistenceMessage(const shared_ptr<api::StorageMessage>& msg)
{
    api::ReturnCode errorCode(api::ReturnCode::OK);
    LOG(spam, "Received %s. Attempting to queue it.", msg->getType().getName().c_str());

    if (_use_async_message_handling_on_schedule) {
       auto result = _filestorHandler->schedule_and_get_next_async_message(msg);
       if (result.was_scheduled()) {
           if (result.has_async_message()) {
               getThreadLocalHandler().processLockedMessage(result.release_async_message());
           }
           return true;
       }
    } else {
        if (_filestorHandler->schedule(msg)) {
            LOG(spam, "Received persistence message %s. Queued it to disk",
                msg->getType().getName().c_str());
            return true;
        }
    }
    switch (_filestorHandler->getDiskState()) {
        case FileStorHandler::CLOSED:
            errorCode = api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node.");
            break;
        case FileStorHandler::AVAILABLE:
            assert(false);
    }
    // If we get here, we failed to schedule message. errorCode says why
    // We need to reply to message (while not having bucket lock)
    if (!msg->getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply = static_cast<api::StorageCommand&>(*msg).makeReply();
        reply->setResult(errorCode);
        LOG(spam, "Received persistence message %s. Returning reply: %s",
            msg->getType().getName().c_str(), errorCode.toString().c_str());
        dispatchUp(reply);
    }
    return true;
}

bool
FileStorManager::onPut(const shared_ptr<api::PutCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Put command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onUpdate(const shared_ptr<api::UpdateCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Update command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onGet(const shared_ptr<api::GetCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRemove(const shared_ptr<api::RemoveCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Remove command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRevert(const shared_ptr<api::RevertCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, 0));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onStatBucket(const std::shared_ptr<api::StatBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onCreateBucket(
        const std::shared_ptr<api::CreateBucketCommand>& cmd)
{
    api::ReturnCode code(api::ReturnCode::OK);
    {
        document::Bucket bucket(cmd->getBucket());
        StorBucketDatabase::WrappedEntry entry(
                _component.getBucketDatabase(bucket.getBucketSpace()).get(
                    bucket.getBucketId(), "FileStorManager::onCreateBucket",
                    StorBucketDatabase::CREATE_IF_NONEXISTING));
        if (entry.preExisted()) {
            LOG(debug, "Got create bucket request for %s which already exists: %s",
                cmd->getBucketId().toString().c_str(),
                entry->getBucketInfo().toString().c_str());
            code = api::ReturnCode(api::ReturnCode::EXISTS, "Bucket already exist");
        } else {
            // Newly created buckets are ready but not active, unless
            // explicitly marked as such by the distributor.
            entry->setBucketInfo(api::BucketInfo(0, 0, 0, 0, 0, true, cmd->getActive()));
            cmd->setPriority(0);
            handlePersistenceMessage(cmd);
            entry.write();
            LOG(debug, "Created bucket %s (node index is %d)",
                cmd->getBucketId().toString().c_str(), _component.getIndex());
            return true;
        }
    }
    std::shared_ptr<api::CreateBucketReply> reply((api::CreateBucketReply*)cmd->makeReply().release());
    reply->setBucketInfo(api::BucketInfo(0, 0, 0, 0, 0, true, cmd->getActive()));
    reply->setResult(code);
    sendUp(reply);
    return true;
}

bool
FileStorManager::onDeleteBucket(const shared_ptr<api::DeleteBucketCommand>& cmd)
{
    {
        document::Bucket bucket(cmd->getBucket());
        StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(),
                                                                                  "FileStorManager::onDeleteBucket"));
        if (!entry.exist()) {
            LOG(debug, "%s was already deleted", cmd->getBucketId().toString().c_str());
            std::shared_ptr<api::StorageReply> reply = cmd->makeReply();
            sendUp(reply);
            return true;
        }

        // If bucket info in command is invalid, it means it was sent by a
        // distributor with an older protocol implementation of
        // DeleteBucketCommand, so we should always allow it to go through
        if (cmd->getBucketInfo().valid()
            && (cmd->getBucketInfo().getChecksum()
                != entry->getBucketInfo().getChecksum()))
        {
            vespalib::asciistream ost;
            ost << "DeleteBucketCommand("
                << cmd->getBucketId().toString()
                << ") did not have up to date bucketinfo. "
                << "Distributor thought we had "
                << cmd->getBucketInfo().toString()
                << ", but storage bucket database contains "
                << entry->getBucketInfo().toString();

            LOG(debug, "Rejecting bucket delete: %s", ost.str().data());
            std::shared_ptr<api::StorageReply> reply = cmd->makeReply();
            static_cast<api::DeleteBucketReply&>(*reply).setBucketInfo(entry->getBucketInfo());
            reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, ost.str()));
            entry.unlock();
            sendUp(reply);
            return true;
        }

        // Forcing max pri on delete bucket for now, so we can't get into
        // a race condition with a create bucket/put coming in after with
        // higher priority.
        cmd->setPriority(0);
        LOG(debug, "Deleting %s", cmd->getBucketId().toString().c_str());
        handlePersistenceMessage(cmd);
        entry.remove();
    }
    _filestorHandler->failOperations(cmd->getBucket(),
                                     api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                                     fmt("Bucket %s about to be deleted anyway",
                                                         cmd->getBucketId().toString().c_str())));
    return true;
}



StorBucketDatabase::WrappedEntry
FileStorManager::ensureConsistentBucket(
        const document::Bucket& bucket,
        api::StorageMessage& msg,
        const char* callerId)
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
                    bucket.getBucketId(), callerId, StorBucketDatabase::CREATE_IF_NONEXISTING));
    assert(entry.exist());
    if (!_component.getBucketDatabase(bucket.getBucketSpace()).isConsistent(entry)) {
        if (!entry.preExisted()) {
            // Don't create empty bucket if merge isn't allowed to continue.
            entry.remove();
        }
        replyDroppedOperation(msg, bucket, api::ReturnCode::ABORTED, "bucket is inconsistently split");
        return StorBucketDatabase::WrappedEntry();
    }

    return entry;
}

bool
FileStorManager::onMergeBucket(const shared_ptr<api::MergeBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(ensureConsistentBucket(cmd->getBucket(), *cmd, "FileStorManager::onMergeBucket"));
    if (!entry.exist()) {
        return true;
    }

    if (!entry.preExisted()) {
        entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
        LOG(debug, "Created bucket %s (node index is %d) due to merge being received.",
            cmd->getBucketId().toString().c_str(), _component.getIndex());
        // Call before writing bucket entry as we need to have bucket
        // lock while calling
        handlePersistenceMessage(cmd);
        entry.write();
    } else {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onGetBucketDiff(const shared_ptr<api::GetBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(ensureConsistentBucket(cmd->getBucket(), *cmd, "FileStorManager::onGetBucketDiff"));
    if (!entry.exist()) {
        return true;
    }
    if (!entry.preExisted()) {
        LOG(debug, "Created bucket %s (node index is %d) due to get bucket diff being received.",
            cmd->getBucketId().toString().c_str(), _component.getIndex());
        entry->info.setTotalDocumentSize(0);
        entry->info.setUsedFileSize(0);
        entry->info.setReady(true);
        // Call before writing bucket entry as we need to have bucket
        // lock while calling
        handlePersistenceMessage(cmd);
        entry.write();
    } else {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::validateApplyDiffCommandBucket(api::StorageMessage& msg, const StorBucketDatabase::WrappedEntry& entry)
{
    if (!entry.exist()) {
        return false;
    }
    BucketSpace bucketSpace(msg.getBucket().getBucketSpace());
    if (!_component.getBucketDatabase(bucketSpace).isConsistent(entry)) {
        document::Bucket bucket(bucketSpace, entry.getBucketId());
        replyDroppedOperation(msg, bucket, api::ReturnCode::ABORTED, "bucket became inconsistent during merging");
        return false;
    }
    return true;
}

bool
FileStorManager::validateDiffReplyBucket(const StorBucketDatabase::WrappedEntry& entry,
                                         const document::Bucket& bucket)
{
    if (!entry.exist()) {
        _filestorHandler->clearMergeStatus(bucket,
                api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND, "Bucket removed during merge"));
        return false;
    }
    if (!_component.getBucketDatabase(bucket.getBucketSpace()).isConsistent(entry)) {
        _filestorHandler->clearMergeStatus(bucket,
                api::ReturnCode(api::ReturnCode::ABORTED, "Bucket became inconsistent during merging"));
        return false;
    }
    return true;
}

bool
FileStorManager::onGetBucketDiffReply(const shared_ptr<api::GetBucketDiffReply>& reply)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*reply, reply->getBucket()));
    if (validateDiffReplyBucket(entry, reply->getBucket())) {
        handlePersistenceMessage(reply);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiff(const shared_ptr<api::ApplyBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (validateApplyDiffCommandBucket(*cmd, entry)) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiffReply(const shared_ptr<api::ApplyBucketDiffReply>& reply)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*reply, reply->getBucket()));
    if (validateDiffReplyBucket(entry, reply->getBucket())) {
        handlePersistenceMessage(reply);
    }
    return true;
}

bool
FileStorManager::onJoinBuckets(const std::shared_ptr<api::JoinBucketsCommand>& cmd)
{
    document::Bucket bucket(cmd->getBucket());
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
                bucket.getBucketId(), "FileStorManager::onJoinBuckets"));
    return handlePersistenceMessage(cmd);
}

bool
FileStorManager::onSplitBucket(const std::shared_ptr<api::SplitBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onSetBucketState(const std::shared_ptr<api::SetBucketStateCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onInternal(const shared_ptr<api::InternalCommand>& msg)
{
    switch (msg->getType()) {
    case GetIterCommand::ID:
    {
        shared_ptr<GetIterCommand> cmd(std::static_pointer_cast<GetIterCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case CreateIteratorCommand::ID:
    {
        shared_ptr<CreateIteratorCommand> cmd(std::static_pointer_cast<CreateIteratorCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case DestroyIteratorCommand::ID:
    {
        spi::Context context(msg->getPriority(), msg->getTrace().getLevel());
        shared_ptr<DestroyIteratorCommand> cmd(std::static_pointer_cast<DestroyIteratorCommand>(msg));
        _provider->destroyIterator(cmd->getIteratorId());
        msg->getTrace().addChild(context.steal_trace());
        return true;
    }
    case ReadBucketList::ID:
    {
        shared_ptr<ReadBucketList> cmd(std::static_pointer_cast<ReadBucketList>(msg));
        handlePersistenceMessage(cmd);
        return true;
    }
    case ReadBucketInfo::ID:
    {
        shared_ptr<ReadBucketInfo> cmd(std::static_pointer_cast<ReadBucketInfo>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case RecheckBucketInfoCommand::ID:
    {
        shared_ptr<RecheckBucketInfoCommand> cmd(std::static_pointer_cast<RecheckBucketInfoCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case AbortBucketOperationsCommand::ID:
    {
        shared_ptr<AbortBucketOperationsCommand> cmd(std::static_pointer_cast<AbortBucketOperationsCommand>(msg));
        handleAbortBucketOperations(cmd);
        return true;
    }
    default:
        return false;
    }
}

void
FileStorManager::handleAbortBucketOperations(const shared_ptr<AbortBucketOperationsCommand>& cmd)
{
    _filestorHandler->abortQueuedOperations(*cmd);
    sendReply(api::StorageReply::SP(cmd->makeReply()));
}

bool
FileStorManager::onInternalReply(const shared_ptr<api::InternalReply>& r)
{
    switch(r->getType()) {
    case GetIterReply::ID:
    {
        sendUp(r);
        return true;
    }
    default:
        return false;
    }
}

void
FileStorManager::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    sendUp(cmd);
}

void
FileStorManager::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    LOG(spam, "Sending reply %s", reply->toString().c_str());

    if (reply->getType() == api::MessageType::INTERNAL_REPLY) {
        std::shared_ptr<api::InternalReply> rep(std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep.get());
        if (onInternalReply(rep)) return;
    }

    // Currently we need to dispatch due to replies sent by remapQueue
    // function in handlerimpl, as filestorthread keeps bucket db lock
    // while running this function
    dispatchUp(reply);
}

void
FileStorManager::sendReplyDirectly(const std::shared_ptr<api::StorageReply>& reply)
{
    LOG(spam, "Sending reply %s", reply->toString().c_str());

    if (reply->getType() == api::MessageType::INTERNAL_REPLY) {
        std::shared_ptr<api::InternalReply> rep(std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep);
        if (onInternalReply(rep)) return;
    }
    sendUp(reply);
}

void
FileStorManager::sendUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    StorageLinkQueued::sendUp(msg);
}

void FileStorManager::onClose()
{
    LOG(debug, "Start closing");
    _bucketExecutorRegistration.reset();
    _resource_usage_listener_registration.reset();
    // Avoid getting config during shutdown
    _configFetcher->close();
    LOG(debug, "Closed _configFetcher.");
    _filestorHandler->close();
    LOG(debug, "Closed _filestorHandler.");
    _closed = true;
    StorageLinkQueued::onClose();
    LOG(debug, "Done closing");
}

void FileStorManager::onFlush(bool downwards)
{
    // Don't delete merges first time around, since threads might be
    // processing them
    LOG(debug, "Start Flushing");
    _filestorHandler->flush(!downwards);
    LOG(debug, "Flushed _filestorHandler->flush(!downwards);");
    for (const auto & thread : _threads) {
        if (thread) {
            thread->flush();
            LOG(debug, "flushed thread[%s]", thread->getThread().getId().c_str());
        }
    }
    uint32_t queue_size = _filestorHandler->getQueueSize();
    if (queue_size > 0) {
        LOG(error, "Operations in persistence layer after flush. This is ok "
                   "during load, but should not happen when flush is called "
                   "during shutdown as load then is supposed to have been "
                   "stopped: Queue size is %u", queue_size);
    }
    StorageLinkQueued::onFlush(downwards);
    LOG(debug, "Done Flushing");
}

void
FileStorManager::reportHtmlStatus(std::ostream& out, const framework::HttpUrlPath& path) const
{
    using vespalib::xml_attribute_escaped;

    bool showStatus = !path.hasAttribute("thread");
    bool verbose    = path.hasAttribute("verbose");
    // Print menu
    out << "<font size=\"-1\">[ <a href=\"../\">Back to top</a>"
        << " | <a href=\"?" << (verbose ? "verbose" : "")
        << "\">Main filestor manager status page</a>"
        << " | <a href=\"?" << (verbose ? "notverbose" : "verbose");
    if (!showStatus) {
        out << "&thread=" << xml_attribute_escaped(path.get("thread", std::string("")));
    }
    out << "\">" << (verbose ? "Less verbose" : "More verbose") << "</a>\n"
        << " ]</font><br><br>\n";

    out << "<p>Using " << _threads.size() << " threads</p>\n";

    _filestorHandler->getStatus(out, path);
}

namespace {
    struct Deactivator {
        StorBucketDatabase::Decision operator() (document::BucketId::Type, StorBucketDatabase::Entry& data) noexcept
        {
            data.info.setActive(false);
            return StorBucketDatabase::Decision::UPDATE;
        }
    };
}

bool
FileStorManager::maintenance_in_all_spaces(const lib::Node& node) const noexcept
{
    for (auto& elem :  _component.getBucketSpaceRepo()) {
        ContentBucketSpace& bucket_space = *elem.second;
        auto derived_cluster_state = bucket_space.getClusterState();
        if (!derived_cluster_state->getNodeState(node).getState().oneOf("m")) {
            return false;
        }
    };
    return true;
}

bool
FileStorManager::should_deactivate_buckets(const ContentBucketSpace& space,
                                           bool node_up_in_space,
                                           bool maintenance_in_all_spaces) noexcept
{
    // Important: this MUST match the semantics in proton::BucketHandler::notifyClusterStateChanged()!
    // Otherwise, the content layer and proton will be out of sync in terms of bucket activation state.
    if (maintenance_in_all_spaces) {
        return false;
    }
    return ((space.getNodeUpInLastNodeStateSeenByProvider() && !node_up_in_space)
           || space.getNodeMaintenanceInLastNodeStateSeenByProvider());
}

void
FileStorManager::maybe_log_received_cluster_state()
{
    if (LOG_WOULD_LOG(debug)) {
        auto cluster_state_bundle = _component.getStateUpdater().getClusterStateBundle();
        auto baseline_state = cluster_state_bundle->getBaselineClusterState();
        LOG(debug, "FileStorManager received baseline cluster state '%s'", baseline_state->toString().c_str());
    }
}

void
FileStorManager::updateState()
{
    maybe_log_received_cluster_state();
    const lib::Node node(_component.getNodeType(), _component.getIndex());
    const bool in_maintenance = maintenance_in_all_spaces(node);

    for (const auto &elem : _component.getBucketSpaceRepo()) {
        BucketSpace bucketSpace(elem.first);
        ContentBucketSpace& contentBucketSpace = *elem.second;
        auto derivedClusterState = contentBucketSpace.getClusterState();
        const bool node_up_in_space = derivedClusterState->getNodeState(node).getState().oneOf("uir");
        if (should_deactivate_buckets(contentBucketSpace, node_up_in_space, in_maintenance)) {
            LOG(debug, "Received cluster state where this node is down; de-activating all buckets "
                       "in database for bucket space %s", bucketSpace.toString().c_str());
            Deactivator deactivator;
            contentBucketSpace.bucketDatabase().for_each_mutable_unordered(
                    std::ref(deactivator), "FileStorManager::updateState");
        }
        contentBucketSpace.setNodeUpInLastNodeStateSeenByProvider(node_up_in_space);
        contentBucketSpace.setNodeMaintenanceInLastNodeStateSeenByProvider(in_maintenance);
        spi::ClusterState spiState(*derivedClusterState, _component.getIndex(),
                                   *contentBucketSpace.getDistribution(),
                                   in_maintenance);
        _provider->setClusterState(bucketSpace, spiState);
    }
}

void
FileStorManager::storageDistributionChanged()
{
    updateState();
}

void
FileStorManager::propagateClusterStates()
{
    auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    for (const auto &elem : _component.getBucketSpaceRepo()) {
        elem.second->setClusterState(clusterStateBundle->getDerivedClusterState(elem.first));
    }
}

void
FileStorManager::handleNewState() noexcept
{
    propagateClusterStates();
    //TODO: Don't update if it isn't necessary (distributor-only change)
    updateState();
}

void FileStorManager::update_reported_state_after_db_init() {
    auto state_lock = _component.getStateUpdater().grabStateChangeLock();
    auto ns = *_component.getStateUpdater().getReportedNodeState();
    ns.setInitProgress(1.0);
    ns.setMinUsedBits(_component.getMinUsedBitsTracker().getMinUsedBits());
    _component.getStateUpdater().setReportedNodeState(ns);
}

void FileStorManager::initialize_bucket_databases_from_provider() {
    framework::MilliSecTimer start_time(_component.getClock());
    size_t bucket_count = 0;
    for (const auto& elem : _component.getBucketSpaceRepo()) {
        const auto bucket_space = elem.first;
        const auto bucket_result = _provider->listBuckets(bucket_space);
        assert(!bucket_result.hasError());
        const auto& buckets = bucket_result.getList();
        LOG(debug, "Fetching bucket info for %zu buckets in space '%s'",
            buckets.size(), bucket_space.toString().c_str());
        auto& db = elem.second->bucketDatabase();

        for (const auto& bucket : buckets) {
            _component.getMinUsedBitsTracker().update(bucket);
            // TODO replace with far more efficient bulk insert API
            auto entry = db.get(bucket, "FileStorManager::initialize_bucket_databases_from_provider",
                                StorBucketDatabase::CREATE_IF_NONEXISTING);
            assert(!entry.preExisted());
            auto spi_bucket = spi::Bucket(document::Bucket(bucket_space, bucket));
            auto provider_result = _provider->getBucketInfo(spi_bucket);
            assert(!provider_result.hasError());
            entry->setBucketInfo(PersistenceUtil::convertBucketInfo(provider_result.getBucketInfo()));
            entry.write();
        }
        bucket_count += buckets.size();
    }
    const double elapsed = start_time.getElapsedTimeAsDouble();
    LOG(info, "Completed listing of %zu buckets in %.2g milliseconds", bucket_count, elapsed);
    _metrics->bucket_db_init_latency.addValue(elapsed);
}

void FileStorManager::complete_internal_initialization() {
    update_reported_state_after_db_init();
    _init_handler.notifyDoneInitializing();
}

void
FileStorManager::execute(const spi::Bucket &bucket, std::unique_ptr<spi::BucketTask> task) {
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
            bucket.getBucketId(), "FileStorManager::execute"));
    if (entry.exist()) {
        auto cmd = std::make_shared<RunTaskCommand>(bucket, std::move(task));
        _filestorHandler->schedule(cmd);
    } else {
        task->fail(bucket);
    }
}

} // storage
