// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "messages.h"
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>

namespace document { class BucketIdFactory; }
namespace vespalib { class ISequencedTaskExecutor; }
namespace storage {

namespace spi {
    struct PersistenceProvider;
    class Context;
}
class PersistenceUtil;
class BucketOwnershipNotifier;
class MessageTracker;

/**
 * Handle async operations that uses a sequenced executor.
 * It is stateless and thread safe.
 */
class AsyncHandler {
    using MessageTrackerUP = std::unique_ptr<MessageTracker>;
public:
    AsyncHandler(const PersistenceUtil&, spi::PersistenceProvider&, BucketOwnershipNotifier  &,
                 vespalib::ISequencedTaskExecutor & executor, const document::BucketIdFactory & bucketIdFactory);
    MessageTrackerUP handlePut(api::PutCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRemove(api::RemoveCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleUpdate(api::UpdateCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRunTask(RunTaskCommand & cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleSetBucketState(api::SetBucketStateCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleDeleteBucket(api::DeleteBucketCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleCreateBucket(api::CreateBucketCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRemoveLocation(api::RemoveLocationCommand& cmd, MessageTrackerUP tracker) const;
    static bool is_async_message(api::MessageType::Id type_id) noexcept;
private:
    bool checkProviderBucketInfoMatches(const spi::Bucket&, const api::BucketInfo&) const;
    static bool tasConditionExists(const api::TestAndSetCommand & cmd);
    bool tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker,
                             spi::Context & context, bool missingDocumentImpliesMatch = false) const;
    const PersistenceUtil            & _env;
    spi::PersistenceProvider         & _spi;
    BucketOwnershipNotifier          & _bucketOwnershipNotifier;
    vespalib::ISequencedTaskExecutor & _sequencedExecutor;
    const document::BucketIdFactory  & _bucketIdFactory;
};

} // storage

