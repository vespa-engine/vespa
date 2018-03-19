// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagebucket.h"
#include "statusmessages.h"
#include "bucketmessages.h"
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/batch.h>

#include <vespa/vespalib/util/exceptions.h>

namespace storage {

document::Bucket
getStorageMessageBucket(const api::StorageMessage& msg)
{
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
        return static_cast<const api::GetCommand&>(msg).getBucket();
    case api::MessageType::PUT_ID:
        return static_cast<const api::PutCommand&>(msg).getBucket();
    case api::MessageType::UPDATE_ID:
        return static_cast<const api::UpdateCommand&>(msg).getBucket();
    case api::MessageType::REMOVE_ID:
        return static_cast<const api::RemoveCommand&>(msg).getBucket();
    case api::MessageType::REVERT_ID:
        return static_cast<const api::RevertCommand&>(msg).getBucket();
    case api::MessageType::STATBUCKET_ID:
        return static_cast<const api::StatBucketCommand&>(msg).getBucket();
    case api::MessageType::BATCHPUTREMOVE_ID:
        return static_cast<const api::BatchPutRemoveCommand&>(msg).getBucket();
    case api::MessageType::REMOVELOCATION_ID:
        return static_cast<const api::RemoveLocationCommand&>(msg).getBucket();
    case api::MessageType::CREATEBUCKET_ID:
        return static_cast<const api::CreateBucketCommand&>(msg).getBucket();
    case api::MessageType::DELETEBUCKET_ID:
        return static_cast<const api::DeleteBucketCommand&>(msg).getBucket();
    case api::MessageType::MERGEBUCKET_ID:
        return static_cast<const api::MergeBucketCommand&>(msg).getBucket();
    case api::MessageType::GETBUCKETDIFF_ID:
        return static_cast<const api::GetBucketDiffCommand&>(msg).getBucket();
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        return static_cast<const api::GetBucketDiffReply&>(msg).getBucket();
    case api::MessageType::APPLYBUCKETDIFF_ID:
        return static_cast<const api::ApplyBucketDiffCommand&>(msg).getBucket();
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        return static_cast<const api::ApplyBucketDiffReply&>(msg).getBucket();
    case api::MessageType::JOINBUCKETS_ID:
        return static_cast<const api::JoinBucketsCommand&>(msg).getBucket();
    case api::MessageType::SPLITBUCKET_ID:
        return static_cast<const api::SplitBucketCommand&>(msg).getBucket();
    case api::MessageType::SETBUCKETSTATE_ID:
        return static_cast<const api::SetBucketStateCommand&>(msg).getBucket();
    case api::MessageType::INTERNAL_ID:
        switch(static_cast<const api::InternalCommand&>(msg).getType()) {
        case RequestStatusPage::ID:
            return document::Bucket();
        case GetIterCommand::ID:
            return static_cast<const GetIterCommand&>(msg).getBucket();
        case CreateIteratorCommand::ID:
            return static_cast<const CreateIteratorCommand&>(msg).getBucket();
        case ReadBucketList::ID:
            return static_cast<const ReadBucketList&>(msg).getBucket();
        case ReadBucketInfo::ID:
            return static_cast<const ReadBucketInfo&>(msg).getBucket();
        case RepairBucketCommand::ID:
            return static_cast<const RepairBucketCommand&>(msg).getBucket();
        case BucketDiskMoveCommand::ID:
            return static_cast<const BucketDiskMoveCommand&>(msg).getBucket();
        case InternalBucketJoinCommand::ID:
            return static_cast<const InternalBucketJoinCommand&>(msg).getBucket();
        case RecheckBucketInfoCommand::ID:
            return static_cast<const RecheckBucketInfoCommand&>(msg).getBucket();
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
