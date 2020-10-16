// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskthread.h"
#include "processallhandler.h"
#include "mergehandler.h"
#include "asynchandler.h"
#include "persistenceutil.h"
#include "provider_error_wrapper.h"
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace storage {

class BucketOwnershipNotifier;

class PersistenceThread final : public DiskThread, public Types
{
public:
    PersistenceThread(vespalib::ISequencedTaskExecutor &, ServiceLayerComponentRegister&,
                      const config::ConfigUri & configUri, spi::PersistenceProvider& provider,
                      FileStorHandler& filestorHandler, FileStorThreadMetrics& metrics);
    ~PersistenceThread() override;

    /** Waits for current operation to be finished. */
    void flush() override;
    framework::Thread& getThread() override { return *_thread; }

    MessageTracker::UP handleGet(api::GetCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleRevert(api::RevertCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleCreateBucket(api::CreateBucketCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleDeleteBucket(api::DeleteBucketCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleCreateIterator(CreateIteratorCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleGetIter(GetIterCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleReadBucketList(ReadBucketList& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleReadBucketInfo(ReadBucketInfo& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleJoinBuckets(api::JoinBucketsCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleSetBucketState(api::SetBucketStateCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleInternalBucketJoin(InternalBucketJoinCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleSplitBucket(api::SplitBucketCommand& cmd, MessageTracker::UP tracker);
    MessageTracker::UP handleRecheckBucketInfo(RecheckBucketInfoCommand& cmd, MessageTracker::UP tracker);

    const AsyncHandler & asyncHandler() const { return _asyncHandler; }
private:
    uint32_t                  _stripeId;
    ServiceLayerComponent::UP _component;
    PersistenceUtil           _env;
    spi::PersistenceProvider& _spi;
    ProcessAllHandler         _processAllHandler;
    MergeHandler              _mergeHandler;
    AsyncHandler              _asyncHandler;
    framework::Thread::UP     _thread;
    std::unique_ptr<BucketOwnershipNotifier> _bucketOwnershipNotifier;

    bool checkProviderBucketInfoMatches(const spi::Bucket&, const api::BucketInfo&) const;

    /**
     * Sanity-checking of join command parameters. Invokes tracker.fail() with
     * an appropriate error and returns false iff the command does not validate
     * OK. Returns true and does not touch the tracker otherwise.
     */
    static bool validateJoinCommand(const api::JoinBucketsCommand& cmd, MessageTracker& tracker);

    // Message handling functions
    MessageTracker::UP handleCommandSplitByType(api::StorageCommand&, MessageTracker::UP tracker);
    void handleReply(api::StorageReply&);

    MessageTracker::UP processMessage(api::StorageMessage& msg, MessageTracker::UP tracker);
    void processLockedMessage(FileStorHandler::LockedMessage lock);

    // Thread main loop
    void run(framework::ThreadHandle&) override;
};

} // storage
