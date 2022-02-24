// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "processallhandler.h"
#include "mergehandler.h"
#include "asynchandler.h"
#include "persistenceutil.h"
#include "splitjoinhandler.h"
#include "simplemessagehandler.h"
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/config-stor-filestor.h>

namespace storage {

class BucketOwnershipNotifier;

/**
 * Handle all messages destined for the persistence layer. The detailed handling
 * happens in other handlers, but is forked out after common setup has been done.
 * Currently metrics are updated so each thread should have its own instance.
 */
class PersistenceHandler : public Types
{
public:
    PersistenceHandler(vespalib::ISequencedTaskExecutor &, const ServiceLayerComponent & component,
                      const vespa::config::content::StorFilestorConfig &, spi::PersistenceProvider &,
                      FileStorHandler &, BucketOwnershipNotifier &, FileStorThreadMetrics&);
    ~PersistenceHandler();

    void processLockedMessage(FileStorHandler::LockedMessage lock) const;

    //TODO Rewrite tests to avoid this api leak
    const AsyncHandler & asyncHandler() const { return _asyncHandler; }
    const SplitJoinHandler & splitjoinHandler() const { return _splitJoinHandler; }
    const SimpleMessageHandler & simpleMessageHandler() const { return _simpleHandler; }

    void set_throttle_merge_feed_ops(bool throttle) noexcept;
private:
    // Message handling functions
    MessageTracker::UP handleCommandSplitByType(api::StorageCommand&, MessageTracker::UP tracker) const;
    MessageTracker::UP handleReply(api::StorageReply&, MessageTracker::UP) const;

    MessageTracker::UP processMessage(api::StorageMessage& msg, MessageTracker::UP tracker) const;

    const framework::Clock  & _clock;
    PersistenceUtil           _env;
    ProcessAllHandler         _processAllHandler;
    MergeHandler              _mergeHandler;
    AsyncHandler              _asyncHandler;
    SplitJoinHandler          _splitJoinHandler;
    SimpleMessageHandler      _simpleHandler;
};

} // storage
