// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ireplaypackethandler.h"
#include <vespa/searchlib/transactionlog/common.h>

namespace proton {

class FeedOperation;
/**
 * Utility class that deserializes packet entries into feed operations
 * during replay from the transaction log and dispatches the feed operations
 * to a given handler class.
 */
class ReplayPacketDispatcher
{
private:
    typedef search::transactionlog::Packet Packet;
    IReplayPacketHandler &_handler;

    template <typename OperationType>
    void replay(OperationType &op, vespalib::nbostream &is, const Packet::Entry &entry);

protected:
    virtual void store(const FeedOperation &op);

public:
    ReplayPacketDispatcher(IReplayPacketHandler &handler);
    virtual ~ReplayPacketDispatcher();

    void replayEntry(const Packet::Entry &entry);
};

} // namespace proton

