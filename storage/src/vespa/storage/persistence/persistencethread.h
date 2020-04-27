// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskthread.h"
#include "processallhandler.h"
#include "mergehandler.h"
#include "diskmoveoperationhandler.h"
#include "persistenceutil.h"
#include "provider_error_wrapper.h"
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/statusmessages.h>

namespace storage {

class BucketOwnershipNotifier;
class TestAndSetHelper;

class PersistenceThread final : public DiskThread, public Types
{
public:
    PersistenceThread(ServiceLayerComponentRegister&, const config::ConfigUri & configUri,
                      spi::PersistenceProvider& provider, FileStorHandler& filestorHandler,
                      FileStorThreadMetrics& metrics, uint16_t deviceIndex);
    ~PersistenceThread();

    /** Waits for current operation to be finished. */
    void flush() override;
    framework::Thread& getThread() override { return *_thread; }

    MessageTracker::UP handlePut(api::PutCommand& cmd, spi::Context & context);
    MessageTracker::UP handleRemove(api::RemoveCommand& cmd, spi::Context & context);
    MessageTracker::UP handleUpdate(api::UpdateCommand& cmd, spi::Context & context);
    MessageTracker::UP handleGet(api::GetCommand& cmd, spi::Context & context);
    MessageTracker::UP handleRevert(api::RevertCommand& cmd, spi::Context & context);
    MessageTracker::UP handleCreateBucket(api::CreateBucketCommand& cmd, spi::Context & context);
    MessageTracker::UP handleDeleteBucket(api::DeleteBucketCommand& cmd, spi::Context & context);
    MessageTracker::UP handleCreateIterator(CreateIteratorCommand& cmd, spi::Context & context);
    MessageTracker::UP handleGetIter(GetIterCommand& cmd, spi::Context & context);
    MessageTracker::UP handleReadBucketList(ReadBucketList& cmd);
    MessageTracker::UP handleReadBucketInfo(ReadBucketInfo& cmd);
    MessageTracker::UP handleJoinBuckets(api::JoinBucketsCommand& cmd, spi::Context & context);
    MessageTracker::UP handleSetBucketState(api::SetBucketStateCommand& cmd);
    MessageTracker::UP handleInternalBucketJoin(InternalBucketJoinCommand& cmd, spi::Context & context);
    MessageTracker::UP handleSplitBucket(api::SplitBucketCommand& cmd, spi::Context & context);
    MessageTracker::UP handleRepairBucket(RepairBucketCommand& cmd);
    MessageTracker::UP handleRecheckBucketInfo(RecheckBucketInfoCommand& cmd);

private:
    uint32_t                  _stripeId;
    PersistenceUtil           _env;
    uint32_t                  _warnOnSlowOperations;
    spi::PersistenceProvider& _spi;
    ProcessAllHandler         _processAllHandler;
    MergeHandler              _mergeHandler;
    DiskMoveOperationHandler  _diskMoveHandler;
    ServiceLayerComponent::UP _component;
    framework::Thread::UP     _thread;
    std::unique_ptr<BucketOwnershipNotifier> _bucketOwnershipNotifier;
    vespalib::Monitor         _flushMonitor;
    bool                      _closed;

    bool checkProviderBucketInfoMatches(const spi::Bucket&, const api::BucketInfo&) const;

    /**
     * Sanity-checking of join command parameters. Invokes tracker.fail() with
     * an appropriate error and returns false iff the command does not validate
     * OK. Returns true and does not touch the tracker otherwise.
     */
    bool validateJoinCommand(const api::JoinBucketsCommand& cmd, MessageTracker& tracker) const;

    // Message handling functions
    MessageTracker::UP handleCommand(api::StorageCommand&);
    MessageTracker::UP handleCommandSplitByType(api::StorageCommand&, spi::Context & context);
    void handleReply(api::StorageReply&);

    MessageTracker::UP processMessage(api::StorageMessage& msg);
    void processMessages(FileStorHandler::LockedMessage & lock);

    // Thread main loop
    void run(framework::ThreadHandle&) override;
    bool checkForError(const spi::Result& response, MessageTracker& tracker);
    spi::Bucket getBucket(const DocumentId& id, const document::Bucket &bucket) const;

    void flushAllReplies(const document::Bucket& bucket, std::vector<MessageTracker::UP>& trackers);

    friend class TestAndSetHelper;
    bool tasConditionExists(const api::TestAndSetCommand & cmd);
    bool tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker,
                             spi::Context & context, bool missingDocumentImpliesMatch = false);
};

} // storage
