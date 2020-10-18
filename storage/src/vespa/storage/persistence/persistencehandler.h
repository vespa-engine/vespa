// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "processallhandler.h"
#include "mergehandler.h"
#include "asynchandler.h"
#include "persistenceutil.h"
#include "provider_error_wrapper.h"
#include "splitjoinhandler.h"
#include "simplemessagehandler.h"
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/config-stor-filestor.h>

namespace storage {

class BucketOwnershipNotifier;

class PersistenceHandler : public Types
{
public:
    PersistenceHandler(vespalib::ISequencedTaskExecutor &, ServiceLayerComponent & component,
                      const vespa::config::content::StorFilestorConfig &, spi::PersistenceProvider &,
                      FileStorHandler &, BucketOwnershipNotifier &, FileStorThreadMetrics&);
    ~PersistenceHandler();

    void processLockedMessage(FileStorHandler::LockedMessage lock) const;

    //TODO Rewrite tests to avoid this api leak
    const AsyncHandler & asyncHandler() const { return _asyncHandler; }
    const SplitJoinHandler & splitjoinHandler() const { return _splitJoinHandler; }
    const SimpleMessageHandler & simpleMessageHandler() const { return _simpleHandler; }
private:
    // Message handling functions
    MessageTracker::UP handleCommandSplitByType(api::StorageCommand&, MessageTracker::UP tracker) const;
    void handleReply(api::StorageReply&) const;

    MessageTracker::UP processMessage(api::StorageMessage& msg, MessageTracker::UP tracker) const;

    PersistenceUtil           _env;
    spi::PersistenceProvider& _spi;
    ProcessAllHandler         _processAllHandler;
    MergeHandler              _mergeHandler;
    AsyncHandler              _asyncHandler;
    SplitJoinHandler          _splitJoinHandler;
    SimpleMessageHandler      _simpleHandler;
};

} // storage
