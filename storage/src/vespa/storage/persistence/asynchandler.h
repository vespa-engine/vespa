// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include <vespa/storageapi/message/persistence.h>

namespace vespalib { class ISequencedTaskExecutor; }
namespace storage {

namespace spi {
    struct PersistenceProvider;
    class Context;
}
struct PersistenceUtil;

class AsyncHandler : public Types {

public:
    AsyncHandler(const PersistenceUtil&, spi::PersistenceProvider&, vespalib::ISequencedTaskExecutor & executor);
    MessageTrackerUP handlePut(api::PutCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRemove(api::RemoveCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleUpdate(api::UpdateCommand& cmd, MessageTrackerUP tracker) const;
private:
    static bool tasConditionExists(const api::TestAndSetCommand & cmd);
    bool tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker,
                             spi::Context & context, bool missingDocumentImpliesMatch = false) const;
    const PersistenceUtil            & _env;
    spi::PersistenceProvider         & _spi;
    vespalib::ISequencedTaskExecutor & _sequencedExecutor;
};

} // storage

