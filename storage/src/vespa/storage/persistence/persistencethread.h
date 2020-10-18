// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diskthread.h"
#include "processallhandler.h"
#include "mergehandler.h"
#include "asynchandler.h"
#include "persistenceutil.h"
#include "provider_error_wrapper.h"
#include "splitjoinhandler.h"
#include "simplemessagehandler.h"
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/config-stor-filestor.h>

namespace storage {

class BucketOwnershipNotifier;

class PersistenceThread final : public DiskThread, public Types
{
public:
    PersistenceThread(vespalib::ISequencedTaskExecutor &, ServiceLayerComponentRegister &,
                      const vespa::config::content::StorFilestorConfig &, spi::PersistenceProvider &,
                      FileStorHandler &, BucketOwnershipNotifier &, FileStorThreadMetrics&);
    ~PersistenceThread() override;

    /** Waits for current operation to be finished. */
    void flush() override;
    framework::Thread& getThread() override { return *_thread; }

    //TODO Rewrite tests to avoid this api leak
    const AsyncHandler & asyncHandler() const { return _asyncHandler; }
    const SplitJoinHandler & splitjoinHandler() const { return _splitJoinHandler; }
    const SimpleMessageHandler & simpleMessageHandler() const { return _simpleHandler; }
private:
    uint32_t                  _stripeId;
    ServiceLayerComponent::UP _component;
    PersistenceUtil           _env;
    spi::PersistenceProvider& _spi;
    ProcessAllHandler         _processAllHandler;
    MergeHandler              _mergeHandler;
    AsyncHandler              _asyncHandler;
    SplitJoinHandler          _splitJoinHandler;
    SimpleMessageHandler      _simpleHandler;
    framework::Thread::UP     _thread;

    // Message handling functions
    MessageTracker::UP handleCommandSplitByType(api::StorageCommand&, MessageTracker::UP tracker);
    void handleReply(api::StorageReply&);

    MessageTracker::UP processMessage(api::StorageMessage& msg, MessageTracker::UP tracker);
    void processLockedMessage(FileStorHandler::LockedMessage lock);

    // Thread main loop
    void run(framework::ThreadHandle&) override;
};

} // storage
