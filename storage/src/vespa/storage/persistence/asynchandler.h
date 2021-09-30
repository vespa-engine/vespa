// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include "messages.h"
#include <vespa/storageapi/message/persistence.h>

namespace document { class BucketIdFactory; }
namespace vespalib { class ISequencedTaskExecutor; }
namespace storage {

namespace spi {
    struct PersistenceProvider;
    class Context;
}
class PersistenceUtil;

/**
 * Handle async operations that uses a sequenced executor.
 * It is stateless and thread safe.
 */
class AsyncHandler : public Types {
public:
    AsyncHandler(const PersistenceUtil&, spi::PersistenceProvider&, vespalib::ISequencedTaskExecutor & executor,
                 const document::BucketIdFactory & bucketIdFactory);
    MessageTrackerUP handlePut(api::PutCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRemove(api::RemoveCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleUpdate(api::UpdateCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRunTask(RunTaskCommand & cmd, MessageTrackerUP tracker) const;
    static bool is_async_message(api::MessageType::Id type_id) noexcept;
private:
    static bool tasConditionExists(const api::TestAndSetCommand & cmd);
    bool tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker,
                             spi::Context & context, bool missingDocumentImpliesMatch = false) const;
    const PersistenceUtil            & _env;
    spi::PersistenceProvider         & _spi;
    vespalib::ISequencedTaskExecutor & _sequencedExecutor;
    const document::BucketIdFactory  & _bucketIdFactory;
};

} // storage

