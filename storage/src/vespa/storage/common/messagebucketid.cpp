// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagebucketid.h"
#include "statusmessages.h"
#include "bucketmessages.h"
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/batch.h>

#include <vespa/vespalib/util/exceptions.h>

namespace storage {

document::BucketId
getStorageMessageBucketId(const api::StorageMessage& msg)
{
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
        return static_cast<const api::GetCommand&>(msg).getBucketId();
    case api::MessageType::PUT_ID:
        return static_cast<const api::PutCommand&>(msg).getBucketId();
    case api::MessageType::UPDATE_ID:
        return static_cast<const api::UpdateCommand&>(msg).getBucketId();
    case api::MessageType::REMOVE_ID:
        return static_cast<const api::RemoveCommand&>(msg).getBucketId();
    case api::MessageType::REVERT_ID:
        return static_cast<const api::RevertCommand&>(msg).getBucketId();
    case api::MessageType::STATBUCKET_ID:
        return static_cast<const api::StatBucketCommand&>(msg).getBucketId();
    case api::MessageType::MULTIOPERATION_ID:
        return static_cast<const api::MultiOperationCommand&>(msg)
                .getBucketId();
    case api::MessageType::BATCHPUTREMOVE_ID:
        return static_cast<const api::BatchPutRemoveCommand&>(msg)
            .getBucketId();
    case api::MessageType::REMOVELOCATION_ID:
        return static_cast<const api::RemoveLocationCommand&>(msg)
                .getBucketId();
    case api::MessageType::CREATEBUCKET_ID:
        return static_cast<const api::CreateBucketCommand&>(msg).getBucketId();
    case api::MessageType::DELETEBUCKET_ID:
        return static_cast<const api::DeleteBucketCommand&>(msg).getBucketId();
    case api::MessageType::MERGEBUCKET_ID:
        return static_cast<const api::MergeBucketCommand&>(msg).getBucketId();
    case api::MessageType::GETBUCKETDIFF_ID:
        return static_cast<const api::GetBucketDiffCommand&>(msg).getBucketId();
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        return static_cast<const api::GetBucketDiffReply&>(msg).getBucketId();
    case api::MessageType::APPLYBUCKETDIFF_ID:
        return static_cast<const api::ApplyBucketDiffCommand&>(msg)
                .getBucketId();
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        return static_cast<const api::ApplyBucketDiffReply&>(msg).getBucketId();

    case api::MessageType::JOINBUCKETS_ID:
        return static_cast<const api::JoinBucketsCommand&>(msg).getBucketId();
    case api::MessageType::SPLITBUCKET_ID:
        return static_cast<const api::SplitBucketCommand&>(msg).getBucketId();
    case api::MessageType::SETBUCKETSTATE_ID:
        return static_cast<const api::SetBucketStateCommand&>(msg).getBucketId();

    case api::MessageType::INTERNAL_ID:
        switch(static_cast<const api::InternalCommand&>(msg).getType()) {
        case RequestStatusPage::ID:
            return document::BucketId();
        case GetIterCommand::ID:
            return static_cast<const GetIterCommand&>(msg).getBucketId();
        case CreateIteratorCommand::ID:
            return static_cast<const CreateIteratorCommand&>(msg)
                .getBucketId();
        case ReadBucketList::ID:
            return document::BucketId();
        case ReadBucketInfo::ID:
            return static_cast<const ReadBucketInfo&>(msg).getBucketId();
        case RepairBucketCommand::ID:
            return static_cast<const RepairBucketCommand&>(msg).getBucketId();
        case BucketDiskMoveCommand::ID:
            return static_cast<const BucketDiskMoveCommand&>(msg).getBucketId();
        case InternalBucketJoinCommand::ID:
            return static_cast<const InternalBucketJoinCommand&>(msg)
                    .getBucketId();
        case RecheckBucketInfoCommand::ID:
            return static_cast<const RecheckBucketInfoCommand&>(msg).getBucketId();
        default:
            break;
        }
    default:
        break;
    }
    throw vespalib::IllegalArgumentException(
            "Message of type " + msg.toString() + " was not expected. Don't "
            "know how to calculate bucket this message operates on.",
            VESPA_STRLOC);
}

}
