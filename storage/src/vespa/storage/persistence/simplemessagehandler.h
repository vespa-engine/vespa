// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "types.h"
#include "messages.h"
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storageapi/message/persistence.h>

namespace storage {

namespace spi { struct PersistenceProvider; }
class PersistenceUtil;

/**
 * Handles most of the messages that are 'simple' to handle and do not
 * logically belong together with any particular group.
 * It is stateless and thread safe.
 */
class SimpleMessageHandler : public Types {
public:
    SimpleMessageHandler(const PersistenceUtil&, spi::PersistenceProvider&);
    MessageTrackerUP handleGet(api::GetCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRevert(api::RevertCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleCreateIterator(CreateIteratorCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleGetIter(GetIterCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleReadBucketList(ReadBucketList& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleReadBucketInfo(ReadBucketInfo& cmd, MessageTrackerUP tracker) const;
private:
    const PersistenceUtil    & _env;
    spi::PersistenceProvider & _spi;
};

} // storage

