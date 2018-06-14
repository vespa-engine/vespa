// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replaypacketdispatcher.h"
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/util/serializableexceptions.h>

using vespalib::make_string;
using vespalib::IllegalStateException;

namespace proton {

template <typename OperationType>
void
ReplayPacketDispatcher::replay(OperationType &op, vespalib::nbostream &is, const Packet::Entry &entry)
{
    op.deserialize(is, _handler.getDeserializeRepo());
    op.setSerialNum(entry.serial());
    store(op);
    _handler.replay(op);
}


ReplayPacketDispatcher::ReplayPacketDispatcher(IReplayPacketHandler &handler)
    : _handler(handler)
{
}


void
ReplayPacketDispatcher::replayEntry(const Packet::Entry &entry)
{
    vespalib::nbostream is(entry.data().c_str(), entry.data().size());
    switch (entry.type()) {
    case FeedOperation::PUT: {
        PutOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::REMOVE: {
        RemoveOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::UPDATE_42:
      case FeedOperation::UPDATE: {
        UpdateOperation op(static_cast<FeedOperation::Type>(entry.type()));
        replay(op, is, entry);
        break;
    } case FeedOperation::NOOP: {
        NoopOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::NEW_CONFIG: {
        NewConfigOperation op(entry.serial(), _handler.getNewConfigStreamHandler());
        op.deserialize(is, _handler.getDeserializeRepo());
        _handler.replay(op);
        break;
    } case FeedOperation::WIPE_HISTORY: {
        WipeHistoryOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::DELETE_BUCKET: {
        DeleteBucketOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::SPLIT_BUCKET: {
        SplitBucketOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::JOIN_BUCKETS: {
        JoinBucketsOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::PRUNE_REMOVED_DOCUMENTS: {
        PruneRemovedDocumentsOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::SPOOLER_REPLAY_START: {
        SpoolerReplayStartOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::SPOOLER_REPLAY_COMPLETE: {
        SpoolerReplayCompleteOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::MOVE: {
        MoveOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::CREATE_BUCKET: {
        CreateBucketOperation op;
        replay(op, is, entry);
        break;
    } case FeedOperation::COMPACT_LID_SPACE: {
        CompactLidSpaceOperation op;
        replay(op, is, entry);
        break;
    } default:
        throw IllegalStateException
            (make_string("Got packet entry with unknown type id '%u' from TLS",
                         entry.type()));
    }
    if (is.size() > 0) {
        throw document::DeserializeException
            (make_string("Too much data in packet entry (type id '%u', %ld bytes)",
                         entry.type(), is.size()));
    }
}


ReplayPacketDispatcher::~ReplayPacketDispatcher() = default;


void
ReplayPacketDispatcher::store(const FeedOperation &)
{
}

} // namespace proton
